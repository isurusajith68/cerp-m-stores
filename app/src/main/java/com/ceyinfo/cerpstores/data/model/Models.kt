package com.ceyinfo.cerpstores.data.model

import com.google.gson.annotations.SerializedName

// ── API Response wrapper ──

data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null,
    val total: Int? = null,
    val page: Int? = null,
    val limit: Int? = null
)

// ── Auth ──

data class LoginRequest(
    val email: String,
    val password: String,
    val organizationId: String? = null
)

data class LoginData(
    val userId: String,
    val email: String,
    val organizationId: String,
    val isOwner: Boolean,
    val isGypsy: Boolean = false,
    val selectOrgRequired: Boolean? = null,
    val organizations: List<Organization>? = null
)

data class Organization(
    val id: String,
    val name: String
)

data class VerifyModuleData(
    val userId: String,
    val email: String,
    val organizationId: String,
    val organizationName: String,
    val employeeName: String? = null,
    val roleLabel: String? = null,
    val isOwner: Boolean,
    val isGypsy: Boolean = false,
    val businessUnitId: String? = null,
    val businessUnitName: String? = null,
    val businessUnitLevel: String? = null
)

// ── Store Mobile: Role + Permissions ─────────────────────────────

/**
 * `/store-mobile/my-role` response. The `stores` list here mirrors
 * cash-book's site list shape (bu_id/bu_name aliases) so the mobile UI
 * can reuse the same adapters cash-book uses for the BU strip.
 */
data class MyRoleData(
    val role: String,                                // 'all' | 'member' | 'none'
    @SerializedName("is_owner") val isOwner: Boolean = false,
    @SerializedName("role_labels") val roleLabels: List<String>? = null,
    val stores: List<RoleStoreEntry> = emptyList()
)

data class RoleStoreEntry(
    @SerializedName("bu_id") val buId: String,
    @SerializedName("bu_name") val buName: String,
    val code: String? = null,
    val level: String? = null
)

/**
 * `/store-mobile/my-permissions` response. Cached in SessionManager and
 * consulted by `isEntityAllowed()` / `canPerformAction()` to gate the
 * dashboard tiles, the New-Transaction sheet rows, and per-screen buttons.
 */
data class MyPermissionsData(
    @SerializedName("is_owner") val isOwner: Boolean = false,
    val entities: Map<String, EntityPermissions> = emptyMap()
)

data class EntityPermissions(
    val allowed: Boolean = false,
    @SerializedName("entity_blocked") val entityBlocked: Boolean = false,
    @SerializedName("allowed_actions") val allowedActions: List<String> = emptyList(),
    val permissions: List<AclPermissionRow> = emptyList(),
    val transitions: List<AclTransitionRow> = emptyList()
)

data class AclPermissionRow(
    @SerializedName("state_id") val stateId: String? = null,
    @SerializedName("state_code") val stateCode: String? = null,
    @SerializedName("access_type") val accessType: String,
    @SerializedName("target_code") val targetCode: String,
    val permission: String
)

data class AclTransitionRow(
    @SerializedName("from_state_code") val fromStateCode: String? = null,
    @SerializedName("to_state_code") val toStateCode: String,
    @SerializedName("transition_code") val transitionCode: String,
    @SerializedName("transition_name") val transitionName: String,
    @SerializedName("action_icon") val actionIcon: String? = null
)

// ── Store Mobile: Stores (picker source for "+ Add" flows) ───────

data class MyStore(
    @SerializedName("store_id") val storeId: String,
    val code: String? = null,
    val name: String,
    val type: String? = null,
    @SerializedName("business_unit_id") val businessUnitId: String? = null,
    @SerializedName("business_unit_name") val businessUnitName: String? = null,
    val location: String? = null,
    @SerializedName("is_active") val isActive: Boolean = true
)

// ── Store Mobile: Dashboard ──────────────────────────────────────

data class DashboardStats(
    val grns: EntityCounts = EntityCounts(),
    @SerializedName("store_issues") val storeIssues: EntityCounts = EntityCounts(),
    @SerializedName("stock_transfers") val stockTransfers: EntityCounts = EntityCounts(),
    @SerializedName("material_returns") val materialReturns: EntityCounts = EntityCounts(),
    @SerializedName("stock_adjustments") val stockAdjustments: EntityCounts = EntityCounts(),
    @SerializedName("low_stock_items") val lowStockItems: Int = 0
)

data class EntityCounts(
    val total: Int = 0,
    val draft: Int = 0,
    val submitted: Int = 0,
    val approved: Int = 0
)

// ── GRN (Goods Received Note) ────────────────────────────────────

/**
 * Row shape returned by `/store-mobile/grns` (list) and used as the header
 * of `/store-mobile/grns/:id` (detail). The list view doesn't carry
 * `items`; the detail view populates it from store_grn_details + the
 * material name/SKU + unit symbol joins on the backend.
 */
data class Grn(
    val id: String,
    @SerializedName("grn_number") val grnNumber: String,
    @SerializedName("grn_date") val grnDate: String? = null,
    @SerializedName("receipt_date") val receiptDate: String? = null,
    val status: String,
    @SerializedName("total_quantity") val totalQuantity: Double = 0.0,
    @SerializedName("total_amount") val totalAmount: Double = 0.0,
    @SerializedName("invoice_number") val invoiceNumber: String? = null,
    @SerializedName("invoice_date") val invoiceDate: String? = null,
    @SerializedName("po_id") val poId: String? = null,
    @SerializedName("po_reference") val poReference: String? = null,
    @SerializedName("po_number") val poNumber: String? = null,
    @SerializedName("supplier_id") val supplierId: String? = null,
    @SerializedName("supplier_name") val supplierName: String? = null,
    @SerializedName("store_id") val storeId: String,
    @SerializedName("store_name") val storeName: String? = null,
    @SerializedName("store_code") val storeCode: String? = null,
    @SerializedName("business_unit_id") val businessUnitId: String? = null,
    @SerializedName("business_unit_name") val businessUnitName: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    val remarks: String? = null,
    // Detail-only — null on list responses
    val items: List<GrnLineItem>? = null
)

// ── Lookups (mobile-friendly clones at /store-mobile/lookup/*) ────

data class Material(
    @SerializedName("material_id") val materialId: String,
    val sku: String? = null,
    val name: String,
    @SerializedName("base_unit_id") val baseUnitId: String? = null,
    @SerializedName("base_unit_symbol") val baseUnitSymbol: String? = null,
    @SerializedName("base_unit_name") val baseUnitName: String? = null
)

data class Supplier(
    @SerializedName("supplier_id") val supplierId: String,
    @SerializedName("supplier_name") val supplierName: String,
    @SerializedName("supplier_code") val supplierCode: String? = null
)

// ── GRN Create payload ───────────────────────────────────────────

/**
 * POST body for `/store-mobile/grns`. Backend pulls `business_unit_id`
 * and `module_instance_id` from `store_id` so neither needs to be sent.
 * Dates are ISO yyyy-MM-dd; the backend trims to date when storing.
 */
data class CreateGrnRequest(
    @SerializedName("store_id") val storeId: String,
    @SerializedName("grn_date") val grnDate: String,
    @SerializedName("receipt_date") val receiptDate: String,
    @SerializedName("supplier_id") val supplierId: String? = null,
    @SerializedName("po_id") val poId: String? = null,
    @SerializedName("po_reference") val poReference: String? = null,
    @SerializedName("invoice_number") val invoiceNumber: String? = null,
    @SerializedName("invoice_date") val invoiceDate: String? = null,
    val remarks: String? = null,
    val items: List<CreateGrnLineRequest>
)

data class CreateGrnLineRequest(
    @SerializedName("material_id") val materialId: String,
    @SerializedName("received_quantity") val receivedQuantity: Double,
    @SerializedName("accepted_quantity") val acceptedQuantity: Double? = null,
    @SerializedName("rejected_quantity") val rejectedQuantity: Double? = null,
    @SerializedName("unit_price") val unitPrice: Double,
    @SerializedName("total_amount") val totalAmount: Double? = null,
    @SerializedName("batch_number") val batchNumber: String? = null,
    @SerializedName("expiry_date") val expiryDate: String? = null,
    @SerializedName("manufacturing_date") val manufacturingDate: String? = null,
    val remarks: String? = null
)

data class GrnLineItem(
    @SerializedName("grn_detail_id") val grnDetailId: String,
    @SerializedName("material_id") val materialId: String? = null,
    @SerializedName("material_name") val materialName: String? = null,
    @SerializedName("material_sku") val materialSku: String? = null,
    @SerializedName("unit_symbol") val unitSymbol: String? = null,
    @SerializedName("received_quantity") val receivedQuantity: Double = 0.0,
    @SerializedName("accepted_quantity") val acceptedQuantity: Double? = null,
    @SerializedName("rejected_quantity") val rejectedQuantity: Double? = null,
    @SerializedName("unit_price") val unitPrice: Double = 0.0,
    @SerializedName("total_amount") val totalAmount: Double = 0.0,
    @SerializedName("batch_number") val batchNumber: String? = null,
    @SerializedName("expiry_date") val expiryDate: String? = null,
    @SerializedName("manufacturing_date") val manufacturingDate: String? = null,
    val remarks: String? = null
)
