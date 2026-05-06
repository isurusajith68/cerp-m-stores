package com.ceyinfo.cerpstores.ui.verification

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.load
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.data.model.CreateVerificationItemRequest
import com.ceyinfo.cerpstores.data.model.Material
import com.ceyinfo.cerpstores.data.model.UploadedPhoto
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ItemVerificationPhotoTileBinding
import com.ceyinfo.cerpstores.databinding.SheetAddVerificationItemBinding
import com.ceyinfo.cerpstores.ui.common.MaterialPickerSheet
import com.ceyinfo.cerpstores.util.ImageCompressor
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * Bottom sheet for adding/editing one verification line.
 *
 * Three input dimensions share the same form:
 *   - **catalogue** mode  — pick a material; description + unit auto-fill
 *   - **freetext** mode   — type description + unit symbol manually
 *   - **photos**          — multi-capture (camera) + multi-pick (gallery),
 *                           cap of 5 per line, uploaded inline
 *
 * Photo flow:
 *   1. Camera button → permission check → camera intent → URI written
 *   2. Gallery button → PickMultipleVisualMedia (max 5 - current)
 *   3. For each URI: compress on Dispatchers.IO → POST /upload-photo
 *   4. Server returns { path, url }; sheet appends to its list and
 *      re-renders the strip
 *   5. × on a tile removes that pair from the list
 *   6. Save → CreateVerificationItemRequest carries the full arrays
 *
 * Concurrency model: uploads run sequentially (one at a time) — simpler
 * to reason about than a parallel pool, and keeps the strip's "uploading
 * placeholder" tile representable as a single tail entry. With the 5-cap
 * the worst-case wait is ~5 small JPEG round-trips; acceptable.
 */
object AddVerificationItemSheet {

    private const val MAX_PHOTOS = 5

    /** Wrapper: keep optional Material for display + the API payload. */
    data class LineEntry(
        val material: Material?,
        val description: String,
        val unitSymbol: String?,
        val photoUrls: List<String>,
        val request: CreateVerificationItemRequest,
    )

    fun show(activity: Activity, onSave: (LineEntry) -> Unit) {
        val componentActivity = activity as? ComponentActivity
            ?: run {
                Toast.makeText(activity, "Internal error: activity context", Toast.LENGTH_SHORT).show()
                return
            }

        val dialog = BottomSheetDialog(activity, R.style.Theme_Stores_BottomSheet)
        val binding = SheetAddVerificationItemBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(binding.root)

        var pickedMaterial: Material? = null
        var freetextMode = false

        // Mutable photo state. Strip is rebuilt from `uploaded` on every
        // change. `pendingUploads` drives the trailing spinner tile + the
        // save-button lockout.
        val uploaded = mutableListOf<UploadedPhoto>()
        var pendingUploads = 0
        var pendingCameraUri: Uri? = null

        fun renderStrip() {
            binding.photoStrip.removeAllViews()
            val total = uploaded.size + pendingUploads

            for ((index, photo) in uploaded.withIndex()) {
                val tile = ItemVerificationPhotoTileBinding.inflate(
                    LayoutInflater.from(componentActivity),
                    binding.photoStrip,
                    false,
                )
                tile.photoThumb.load(photo.url) { crossfade(true) }
                tile.btnRemove.setOnClickListener {
                    uploaded.removeAt(index)
                    renderStrip()
                }
                binding.photoStrip.addView(tile.root)
            }
            // One spinner tile per pending upload — gives users a visible
            // sense of "still working" when they batch-pick from gallery.
            repeat(pendingUploads) {
                val tile = ItemVerificationPhotoTileBinding.inflate(
                    LayoutInflater.from(componentActivity),
                    binding.photoStrip,
                    false,
                )
                tile.photoThumb.setImageDrawable(null)
                tile.btnRemove.visibility = View.GONE
                tile.uploadOverlay.visibility = View.VISIBLE
                binding.photoStrip.addView(tile.root)
            }

            binding.photoScroll.visibility = if (total > 0) View.VISIBLE else View.GONE
            binding.tvPhotoCount.text = if (total == 0) "" else "$total / $MAX_PHOTOS"

            val canAddMore = total < MAX_PHOTOS
            binding.btnPhotoCamera.isEnabled = canAddMore
            binding.btnPhotoGallery.isEnabled = canAddMore
        }

        // Per-show unique keys so opening the sheet twice doesn't collide
        // in the activityResultRegistry.
        val keySuffix = System.nanoTime().toString()

        // Sequential upload runner. Decouples the strip rebuild from the
        // launcher callback so gallery batches go through one-by-one.
        suspend fun uploadOne(uri: Uri): UploadedPhoto? {
            return try {
                val compressed: File = withContext(Dispatchers.IO) {
                    ImageCompressor.compress(componentActivity, uri)
                }
                val mediaType = "image/jpeg".toMediaTypeOrNull()
                val body = compressed.asRequestBody(mediaType)
                val part = MultipartBody.Part.createFormData("file", compressed.name, body)
                val resp = ApiClient.getService(componentActivity).uploadPhoto(part)
                val data = resp.body()?.data
                if (resp.isSuccessful && resp.body()?.success == true && data != null) {
                    data
                } else {
                    Toast.makeText(
                        componentActivity,
                        resp.body()?.message
                            ?: componentActivity.getString(R.string.vline_photo_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                    null
                }
            } catch (e: Exception) {
                Toast.makeText(
                    componentActivity,
                    e.message ?: componentActivity.getString(R.string.vline_photo_failed),
                    Toast.LENGTH_LONG,
                ).show()
                null
            }
        }

        fun queueUploads(uris: List<Uri>) {
            // Trim against the cap before kicking off — avoids burning
            // a round-trip on the 6th photo just to discard it.
            val available = MAX_PHOTOS - uploaded.size - pendingUploads
            if (available <= 0) {
                Toast.makeText(
                    componentActivity,
                    R.string.vline_photo_max_reached,
                    Toast.LENGTH_SHORT,
                ).show()
                return
            }
            val accepted = uris.take(available)
            pendingUploads += accepted.size
            renderStrip()

            componentActivity.lifecycleScope.launch {
                for (uri in accepted) {
                    val photo = uploadOne(uri)
                    pendingUploads -= 1
                    if (photo != null) uploaded.add(photo)
                    renderStrip()
                }
            }
        }

        // Camera launcher — single capture per click.
        val cameraLauncher: ActivityResultLauncher<Uri> =
            componentActivity.activityResultRegistry.register(
                "verify_camera_$keySuffix",
                ActivityResultContracts.TakePicture(),
            ) { success ->
                val uri = pendingCameraUri
                if (success && uri != null) queueUploads(listOf(uri))
            }

        // Gallery launcher — multi-pick, system enforces image-only.
        val galleryLauncher: ActivityResultLauncher<PickVisualMediaRequest> =
            componentActivity.activityResultRegistry.register(
                "verify_gallery_$keySuffix",
                ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS),
            ) { uris ->
                if (uris.isNotEmpty()) queueUploads(uris)
            }

        val permissionLauncher: ActivityResultLauncher<String> =
            componentActivity.activityResultRegistry.register(
                "verify_camera_perm_$keySuffix",
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted) {
                    launchCamera(componentActivity, cameraLauncher) { pendingCameraUri = it }
                } else {
                    Toast.makeText(
                        componentActivity,
                        R.string.vline_camera_permission_required,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }

        dialog.setOnDismissListener {
            cameraLauncher.unregister()
            galleryLauncher.unregister()
            permissionLauncher.unregister()
        }

        binding.btnPhotoCamera.setOnClickListener {
            if (uploaded.size + pendingUploads >= MAX_PHOTOS) {
                Toast.makeText(activity, R.string.vline_photo_max_reached, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val granted = ContextCompat.checkSelfPermission(
                componentActivity,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                launchCamera(componentActivity, cameraLauncher) { pendingCameraUri = it }
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        binding.btnPhotoGallery.setOnClickListener {
            if (uploaded.size + pendingUploads >= MAX_PHOTOS) {
                Toast.makeText(activity, R.string.vline_photo_max_reached, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        // Catalogue mode: tap card → MaterialPickerSheet → fill row.
        binding.cardMaterial.setOnClickListener {
            MaterialPickerSheet.show(activity) { material ->
                pickedMaterial = material
                binding.tvMaterialName.text = material.name
                binding.tvMaterialName.setTextColor(activity.getColor(R.color.on_surface))
                val meta = listOfNotNull(
                    material.sku?.takeIf { it.isNotBlank() },
                    material.baseUnitSymbol?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                binding.tvMaterialMeta.text = meta
                binding.tvMaterialMeta.visibility =
                    if (meta.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }

        binding.btnFreetextMode.setOnClickListener {
            freetextMode = true
            pickedMaterial = null
            binding.tvMaterialName.setText(R.string.line_select_material)
            binding.tvMaterialName.setTextColor(activity.getColor(R.color.text_secondary))
            binding.tvMaterialMeta.visibility = View.GONE
            binding.groupCatalogue.visibility = View.GONE
            binding.groupFreetext.visibility = View.VISIBLE
        }
        binding.btnBackToCatalogue.setOnClickListener {
            freetextMode = false
            binding.etDescription.setText("")
            binding.etUnitSymbol.setText("")
            binding.groupFreetext.visibility = View.GONE
            binding.groupCatalogue.visibility = View.VISIBLE
        }

        binding.btnSave.setOnClickListener {
            // Block save while any upload is in flight — landing a line
            // with half-uploaded photos would orphan them in OSS or send
            // an incomplete photo array to the backend.
            if (pendingUploads > 0) {
                Toast.makeText(activity, R.string.vline_photo_uploading, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val qty = binding.etQty.text?.toString()?.toDoubleOrNull() ?: 0.0
            if (qty <= 0.0) {
                binding.tilQty.error = activity.getString(R.string.vline_validation_qty)
                return@setOnClickListener
            }
            binding.tilQty.error = null

            val description: String
            val unitSymbol: String?
            val materialId: String?

            if (freetextMode) {
                description = binding.etDescription.text?.toString()?.trim().orEmpty()
                if (description.isEmpty()) {
                    Toast.makeText(activity, R.string.vline_validation_description, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                materialId = null
                unitSymbol = binding.etUnitSymbol.text?.toString()?.trim().orEmpty()
                if (unitSymbol.isEmpty()) {
                    Toast.makeText(activity, R.string.vline_validation_unit, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            } else {
                val mat = pickedMaterial
                if (mat == null) {
                    Toast.makeText(activity, R.string.line_validation_material, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                description = mat.name
                unitSymbol = mat.baseUnitSymbol
                materialId = mat.materialId
            }

            val photoUrls = uploaded.map { it.url }
            val photoPaths = uploaded.map { it.path }

            val req = CreateVerificationItemRequest(
                materialId = materialId,
                description = description,
                unitId = pickedMaterial?.baseUnitId,
                unitSymbol = unitSymbol,
                verifiedQuantity = qty,
                serialNumber = binding.etSerial.text?.toString()?.trim()?.ifEmpty { null },
                batchNumber = binding.etBatch.text?.toString()?.trim()?.ifEmpty { null },
                photoUrls = photoUrls,
                photoPaths = photoPaths,
                remarks = binding.etRemarks.text?.toString()?.trim()?.ifEmpty { null },
            )

            dialog.dismiss()
            onSave(LineEntry(pickedMaterial, description, unitSymbol, photoUrls, req))
        }

        // Initial render: empty state.
        renderStrip()
        dialog.show()
    }

    private fun launchCamera(
        activity: ComponentActivity,
        launcher: ActivityResultLauncher<Uri>,
        onUri: (Uri) -> Unit,
    ) {
        val dir = File(activity.cacheDir, "photos").apply { mkdirs() }
        val file = File(dir, "verify_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            file,
        )
        onUri(uri)
        launcher.launch(uri)
    }
}
