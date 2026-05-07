package com.ceyinfo.cerpstores.data.model

import com.google.gson.annotations.SerializedName

// ── API Response wrapper ──

data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null,
    @SerializedName("total") private val _total: Int? = null,
    @SerializedName("page") private val _page: Int? = null,
    @SerializedName("limit") private val _limit: Int? = null,
    val pagination: Pagination? = null,
) {
    // paginatedResponse() nests total/page/limit inside a "pagination" object;
    // some older endpoints return them at the top level. Both work with these getters.
    val total: Int? get() = _total ?: pagination?.total
    val page: Int? get() = _page ?: pagination?.page
    val limit: Int? get() = _limit ?: pagination?.limit
}

data class Pagination(
    val total: Int? = null,
    val page: Int? = null,
    val limit: Int? = null,
    @SerializedName("totalPages") val totalPages: Int? = null,
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
    val verifications: EntityCounts = EntityCounts(),
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

// ── Stock Transaction (History) ──────────────────────────────────

data class StockTransaction(
    @SerializedName("stock_tx_id") val stockTxId: String,
    @SerializedName("transaction_number") val transactionNumber: String? = null,
    @SerializedName("transaction_type") val transactionType: String,
    @SerializedName("reference_document") val referenceDocument: String? = null,
    @SerializedName("reference_id") val referenceId: String? = null,
    @SerializedName("item_id") val itemId: String? = null,
    @SerializedName("from_store_id") val fromStoreId: String? = null,
    @SerializedName("to_store_id") val toStoreId: String? = null,
    val quantity: Double = 0.0,
    @SerializedName("unit_price") val unitPrice: Double? = null,
    @SerializedName("batch_number") val batchNumber: String? = null,
    val remarks: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("material_name") val materialName: String? = null,
    @SerializedName("material_sku") val materialSku: String? = null,
    @SerializedName("unit_symbol") val unitSymbol: String? = null,
    @SerializedName("from_store_name") val fromStoreName: String? = null,
    @SerializedName("to_store_name") val toStoreName: String? = null,
    @SerializedName("created_by_name") val createdByName: String? = null,
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

// ── Stock Verification ───────────────────────────────────────────
//
// Header (`Verification`) shape is shared by list + detail responses;
// `items` only populates on detail. Snapshot fields on items
// (systemQuantityAtVerify, deltaQuantity) are populated only after the
// header is APPROVED — they let the detail screen show the variance.

data class Verification(
    val id: String,
    @SerializedName("verification_number") val verificationNumber: String,
    @SerializedName("verification_date") val verificationDate: String? = null,
    val status: String,
    val remarks: String? = null,
    @SerializedName("store_id") val storeId: String,
    @SerializedName("store_name") val storeName: String? = null,
    @SerializedName("store_code") val storeCode: String? = null,
    @SerializedName("business_unit_id") val businessUnitId: String? = null,
    @SerializedName("business_unit_name") val businessUnitName: String? = null,
    @SerializedName("created_by") val createdBy: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("submitted_at") val submittedAt: String? = null,
    @SerializedName("approved_at") val approvedAt: String? = null,
    @SerializedName("items_count") val itemsCount: Int? = null,
    val items: List<VerificationItem>? = null
)

data class VerificationItem(
    @SerializedName("verification_item_id") val verificationItemId: String,
    @SerializedName("material_id") val materialId: String? = null,
    @SerializedName("material_name") val materialName: String? = null,
    @SerializedName("material_sku") val materialSku: String? = null,
    val description: String,
    @SerializedName("unit_id") val unitId: String? = null,
    @SerializedName("unit_symbol") val unitSymbol: String? = null,
    @SerializedName("verified_quantity") val verifiedQuantity: Double = 0.0,
    @SerializedName("serial_number") val serialNumber: String? = null,
    @SerializedName("batch_number") val batchNumber: String? = null,
    @SerializedName("photo_urls") val photoUrls: List<String> = emptyList(),
    @SerializedName("photo_paths") val photoPaths: List<String> = emptyList(),
    @SerializedName("system_quantity_at_verify") val systemQuantityAtVerify: Double? = null,
    @SerializedName("delta_quantity") val deltaQuantity: Double? = null,
    val remarks: String? = null
)

data class CreateVerificationRequest(
    @SerializedName("store_id") val storeId: String,
    @SerializedName("verification_date") val verificationDate: String,
    val remarks: String? = null,
    val items: List<CreateVerificationItemRequest>
)

data class CreateVerificationItemRequest(
    @SerializedName("material_id") val materialId: String? = null,
    val description: String,
    @SerializedName("unit_id") val unitId: String? = null,
    @SerializedName("unit_symbol") val unitSymbol: String? = null,
    @SerializedName("verified_quantity") val verifiedQuantity: Double,
    @SerializedName("serial_number") val serialNumber: String? = null,
    @SerializedName("batch_number") val batchNumber: String? = null,
    @SerializedName("photo_urls") val photoUrls: List<String> = emptyList(),
    @SerializedName("photo_paths") val photoPaths: List<String> = emptyList(),
    val remarks: String? = null
)

/**
 * Body for PATCH .../transition endpoints.
 * [remarks] is optional — the backend currently ignores it for GRN/Issue/Verification,
 * but including it here keeps the model wire-ready for when it's handled server-side.
 */
data class TransitionRequest(
    val action: String,
    val remarks: String? = null,
)

// ── Goods Issue ──────────────────────────────────────────────────────────────

/**
 * Row shape returned by `/store-mobile/issues` (list) and used as the header
 * of `/store-mobile/issues/:id` (detail). The list view doesn't carry
 * `items`; the detail view populates them from store_store_issue_details.
 */
data class Issue(
    val id: String,
    @SerializedName("issue_number") val issueNumber: String,
    @SerializedName("issue_date") val issueDate: String? = null,
    val status: String,
    @SerializedName("total_quantity") val totalQuantity: Double = 0.0,
    val remarks: String? = null,
    @SerializedName("store_id") val storeId: String,
    @SerializedName("store_name") val storeName: String? = null,
    @SerializedName("store_code") val storeCode: String? = null,
    @SerializedName("business_unit_id") val businessUnitId: String? = null,
    @SerializedName("business_unit_name") val businessUnitName: String? = null,
    @SerializedName("department_id") val departmentId: String? = null,
    @SerializedName("department_name") val departmentName: String? = null,
    @SerializedName("issued_to_id") val issuedToId: String? = null,
    @SerializedName("issued_to_name") val issuedToName: String? = null,
    @SerializedName("to_department") val toDepartment: String? = null,
    @SerializedName("issued_to") val issuedTo: String? = null,
    @SerializedName("items_count") val itemsCount: Int? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    // Detail-only
    val items: List<IssueLineItem>? = null
)

data class IssueLineItem(
    @SerializedName("issue_detail_id") val issueDetailId: String,
    @SerializedName("material_id") val materialId: String? = null,
    @SerializedName("material_name") val materialName: String? = null,
    @SerializedName("material_sku") val materialSku: String? = null,
    @SerializedName("unit_symbol") val unitSymbol: String? = null,
    @SerializedName("issued_quantity") val issuedQuantity: Double = 0.0,
    @SerializedName("batch_number") val batchNumber: String? = null,
    @SerializedName("unit_price") val unitPrice: Double? = null,
    val remarks: String? = null
)

data class CreateIssueRequest(
    @SerializedName("store_id") val storeId: String,
    @SerializedName("issue_date") val issueDate: String,
    @SerializedName("department_id") val departmentId: String? = null,
    @SerializedName("issued_to_id") val issuedToId: String? = null,
    @SerializedName("to_department") val toDepartment: String? = null,
    @SerializedName("issued_to") val issuedTo: String? = null,
    @SerializedName("requisition_id") val requisitionId: String? = null,
    val remarks: String? = null,
    val items: List<CreateIssueLineRequest>
)

data class CreateIssueLineRequest(
    @SerializedName("material_id") val materialId: String,
    @SerializedName("issued_quantity") val issuedQuantity: Double,
    @SerializedName("batch_number") val batchNumber: String? = null,
    @SerializedName("unit_price") val unitPrice: Double? = null,
    val remarks: String? = null
)

// ── Stock Transfer ───────────────────────────────────────────────────────────

/**
 * Row shape returned by `/store-mobile/transfers` (list) and as the header of
 * `/store-mobile/transfers/:id` (detail). `items` is null on list responses.
 */
data class Transfer(
    val id: String,
    @SerializedName("transfer_number") val transferNumber: String,
    @SerializedName("transfer_date") val transferDate: String? = null,
    val status: String,
    @SerializedName("total_quantity") val totalQuantity: Double = 0.0,
    val remarks: String? = null,
    @SerializedName("from_store_id") val fromStoreId: String,
    @SerializedName("to_store_id") val toStoreId: String,
    @SerializedName("from_store_name") val fromStoreName: String? = null,
    @SerializedName("from_store_code") val fromStoreCode: String? = null,
    @SerializedName("to_store_name") val toStoreName: String? = null,
    @SerializedName("to_store_code") val toStoreCode: String? = null,
    @SerializedName("business_unit_id") val businessUnitId: String? = null,
    @SerializedName("business_unit_name") val businessUnitName: String? = null,
    @SerializedName("received_at") val receivedAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    // Detail-only
    val items: List<TransferLineItem>? = null
)

data class TransferLineItem(
    @SerializedName("transfer_detail_id") val transferDetailId: String,
    @SerializedName("material_id") val materialId: String? = null,
    @SerializedName("material_name") val materialName: String? = null,
    @SerializedName("material_sku") val materialSku: String? = null,
    @SerializedName("unit_symbol") val unitSymbol: String? = null,
    @SerializedName("transfer_quantity") val transferQuantity: Double = 0.0,
    @SerializedName("received_quantity") val receivedQuantity: Double? = null,
    @SerializedName("batch_number") val batchNumber: String? = null,
    @SerializedName("unit_price") val unitPrice: Double? = null,
    val remarks: String? = null
)

data class CreateTransferRequest(
    @SerializedName("from_store_id") val fromStoreId: String,
    @SerializedName("to_store_id") val toStoreId: String,
    @SerializedName("transfer_date") val transferDate: String,
    val remarks: String? = null,
    val items: List<CreateTransferLineRequest>
)

data class CreateTransferLineRequest(
    @SerializedName("material_id") val materialId: String,
    @SerializedName("transfer_quantity") val transferQuantity: Double,
    @SerializedName("batch_number") val batchNumber: String? = null,
    @SerializedName("unit_price") val unitPrice: Double? = null,
    val remarks: String? = null
)

// ── Material Return ──────────────────────────────────────────────────────────

data class MaterialReturn(
    val id: String,
    @SerializedName("return_number") val returnNumber: String,
    @SerializedName("return_date") val returnDate: String? = null,
    @SerializedName("return_type") val returnType: String,
    @SerializedName("from_store_id") val fromStoreId: String,
    @SerializedName("to_store_id") val toStoreId: String? = null,
    @SerializedName("supplier_id") val supplierId: String? = null,
    @SerializedName("grn_reference_id") val grnReferenceId: String? = null,
    val status: String,
    val remarks: String? = null,
    @SerializedName("from_store_name") val fromStoreName: String? = null,
    @SerializedName("to_store_name") val toStoreName: String? = null,
    @SerializedName("supplier_name") val supplierName: String? = null,
    @SerializedName("grn_reference_number") val grnReferenceNumber: String? = null,
    @SerializedName("business_unit_id") val businessUnitId: String? = null,
    @SerializedName("items_count") val itemsCount: Int? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    val items: List<ReturnLineItem>? = null
)

data class ReturnLineItem(
    @SerializedName("return_detail_id") val returnDetailId: String,
    @SerializedName("material_id") val materialId: String? = null,
    @SerializedName("material_name") val materialName: String? = null,
    @SerializedName("material_sku") val materialSku: String? = null,
    @SerializedName("unit_symbol") val unitSymbol: String? = null,
    @SerializedName("return_quantity") val returnQuantity: Double = 0.0,
    @SerializedName("unit_price") val unitPrice: Double? = null,
    @SerializedName("batch_number") val batchNumber: String? = null,
    val reason: String? = null
)

data class CreateReturnRequest(
    @SerializedName("from_store_id") val fromStoreId: String,
    @SerializedName("to_store_id") val toStoreId: String? = null,
    @SerializedName("supplier_id") val supplierId: String? = null,
    @SerializedName("return_date") val returnDate: String,
    @SerializedName("return_type") val returnType: String,
    val remarks: String? = null,
    val items: List<CreateReturnLineRequest>
)

data class CreateReturnLineRequest(
    @SerializedName("material_id") val materialId: String,
    @SerializedName("return_quantity") val returnQuantity: Double,
    @SerializedName("batch_number") val batchNumber: String? = null,
    @SerializedName("unit_price") val unitPrice: Double? = null,
    val reason: String? = null
)

// ── Stock Adjustment ─────────────────────────────────────────────────────────

data class StockAdjustment(
    val id: String,
    @SerializedName("adjustment_number") val adjustmentNumber: String,
    @SerializedName("adjustment_date") val adjustmentDate: String? = null,
    @SerializedName("store_id") val storeId: String,
    @SerializedName("adjustment_type") val adjustmentType: String,
    val status: String,
    val remarks: String? = null,
    @SerializedName("store_name") val storeName: String? = null,
    @SerializedName("business_unit_id") val businessUnitId: String? = null,
    @SerializedName("items_count") val itemsCount: Int? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    val items: List<AdjustmentLineItem>? = null
)

data class AdjustmentLineItem(
    @SerializedName("adjustment_detail_id") val adjustmentDetailId: String,
    @SerializedName("material_id") val materialId: String? = null,
    @SerializedName("material_name") val materialName: String? = null,
    @SerializedName("material_sku") val materialSku: String? = null,
    @SerializedName("unit_symbol") val unitSymbol: String? = null,
    @SerializedName("adjusted_quantity") val adjustedQuantity: Double = 0.0,
    @SerializedName("batch_number") val batchNumber: String? = null,
    val reason: String? = null
)

data class CreateAdjustmentRequest(
    @SerializedName("store_id") val storeId: String,
    @SerializedName("adjustment_date") val adjustmentDate: String,
    @SerializedName("adjustment_type") val adjustmentType: String,
    val remarks: String? = null,
    val items: List<CreateAdjustmentLineRequest>
)

data class CreateAdjustmentLineRequest(
    @SerializedName("material_id") val materialId: String,
    @SerializedName("adjusted_quantity") val adjustedQuantity: Double,
    @SerializedName("batch_number") val batchNumber: String? = null,
    val reason: String? = null
)

/** Lightweight store entry used in the to-store picker. */
data class StoreInfo(
    @SerializedName("store_id") val storeId: String,
    val code: String? = null,
    val name: String,
    val type: String? = null,
    @SerializedName("business_unit_id") val businessUnitId: String? = null,
    @SerializedName("business_unit_name") val businessUnitName: String? = null,
    val location: String? = null
)

// ── HR Lookups ───────────────────────────────────────────────────────────────

data class Department(
    @SerializedName("department_id") val departmentId: String,
    val name: String,
    val code: String? = null
)

data class Employee(
    @SerializedName("employee_id") val employeeId: String,
    @SerializedName("employee_number") val employeeNumber: String? = null,
    val name: String,
    @SerializedName("department_name") val departmentName: String? = null
)

// ── Inventory ────────────────────────────────────────────────────
//
// One row per (store, material, batch) from store_stock_ledger. Batches
// aren't aggregated — a material with three batches at one store is three
// rows so the per-batch min_stock_level / available_quantity are honored.

data class InventoryRow(
    @SerializedName("ledger_id") val ledgerId: String,
    @SerializedName("store_id") val storeId: String,
    @SerializedName("store_name") val storeName: String? = null,
    @SerializedName("store_code") val storeCode: String? = null,
    @SerializedName("material_id") val materialId: String? = null,
    @SerializedName("material_name") val materialName: String? = null,
    @SerializedName("material_sku") val materialSku: String? = null,
    @SerializedName("batch_number") val batchNumber: String? = null,
    @SerializedName("quantity_on_hand") val quantityOnHand: Double = 0.0,
    @SerializedName("available_quantity") val availableQuantity: Double = 0.0,
    @SerializedName("reserved_quantity") val reservedQuantity: Double = 0.0,
    @SerializedName("min_stock_level") val minStockLevel: Double? = null,
    @SerializedName("is_low_stock") val isLowStock: Boolean = false,
    @SerializedName("unit_symbol") val unitSymbol: String? = null,
    @SerializedName("unit_name") val unitName: String? = null,
    @SerializedName("last_transaction_date") val lastTransactionDate: String? = null,
    @SerializedName("business_unit_id") val businessUnitId: String? = null,
    @SerializedName("business_unit_name") val businessUnitName: String? = null
)

/**
 * Reply shape for `POST /store-mobile/upload-photo`. `path` is the durable
 * OSS object key (store on the row); `url` is a signed URL good for ~30
 * days, fine for the immediate preview but the backend re-signs from path
 * on every detail fetch so callers never see expired URLs.
 */
data class UploadedPhoto(
    val path: String,
    val url: String
)
