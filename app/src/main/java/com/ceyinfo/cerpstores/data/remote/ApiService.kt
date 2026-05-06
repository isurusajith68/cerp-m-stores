package com.ceyinfo.cerpstores.data.remote

import com.ceyinfo.cerpstores.data.model.ApiResponse
import com.ceyinfo.cerpstores.data.model.CreateGrnRequest
import com.ceyinfo.cerpstores.data.model.CreateIssueRequest
import com.ceyinfo.cerpstores.data.model.CreateTransferRequest
import com.ceyinfo.cerpstores.data.model.DashboardStats
import com.ceyinfo.cerpstores.data.model.Department
import com.ceyinfo.cerpstores.data.model.Employee
import com.ceyinfo.cerpstores.data.model.Grn
import com.ceyinfo.cerpstores.data.model.InventoryRow
import com.ceyinfo.cerpstores.data.model.Issue
import com.ceyinfo.cerpstores.data.model.LoginData
import com.ceyinfo.cerpstores.data.model.LoginRequest
import com.ceyinfo.cerpstores.data.model.Material
import com.ceyinfo.cerpstores.data.model.MyPermissionsData
import com.ceyinfo.cerpstores.data.model.MyRoleData
import com.ceyinfo.cerpstores.data.model.MyStore
import com.ceyinfo.cerpstores.data.model.CreateVerificationRequest
import com.ceyinfo.cerpstores.data.model.StoreInfo
import com.ceyinfo.cerpstores.data.model.StockTransaction
import com.ceyinfo.cerpstores.data.model.Supplier
import com.ceyinfo.cerpstores.data.model.Transfer
import com.ceyinfo.cerpstores.data.model.TransitionRequest
import com.ceyinfo.cerpstores.data.model.UploadedPhoto
import com.ceyinfo.cerpstores.data.model.Verification
import com.ceyinfo.cerpstores.data.model.VerifyModuleData
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    // ── Auth ──────────────────────────────────────────────────────────
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginData>>

    @POST("auth/logout")
    suspend fun logout(): Response<ApiResponse<Any>>

    // Reused from cerp-site — same auth pipeline as cerp-m-cash-book.
    @GET("site/verify-module")
    suspend fun verifyModule(): Response<ApiResponse<VerifyModuleData>>

    // ── Store Mobile: Identity & Permissions ──────────────────────────
    @GET("store-mobile/my-role")
    suspend fun getMyRole(): Response<ApiResponse<MyRoleData>>

    @GET("store-mobile/my-permissions")
    suspend fun getMyPermissions(): Response<ApiResponse<MyPermissionsData>>

    @GET("store-mobile/stores/my-stores")
    suspend fun getMyStores(): Response<ApiResponse<List<MyStore>>>

    @GET("store-mobile/dashboard-stats")
    suspend fun getDashboardStats(
        @Query("store_id") storeId: String? = null
    ): Response<ApiResponse<DashboardStats>>

    // ── Store Mobile: GRN ─────────────────────────────────────────────
    @GET("store-mobile/grns")
    suspend fun getGrns(
        @Query("store_id") storeId: String? = null,
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<ApiResponse<List<Grn>>>

    @GET("store-mobile/grns/{id}")
    suspend fun getGrn(@Path("id") id: String): Response<ApiResponse<Grn>>

    @POST("store-mobile/grns")
    suspend fun createGrn(@Body request: CreateGrnRequest): Response<ApiResponse<Grn>>

    @PATCH("store-mobile/grns/{id}/transition")
    suspend fun transitionGrn(
        @Path("id") id: String,
        @Body request: TransitionRequest,
    ): Response<ApiResponse<Grn>>

    // ── Store Mobile: Transactions (History) ─────────────────────────
    @GET("store-mobile/transactions")
    suspend fun getTransactions(
        @Query("store_id") storeId: String? = null,
        @Query("txn_type") txnType: String? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30
    ): Response<ApiResponse<List<StockTransaction>>>

    // ── Store Mobile: Inventory ───────────────────────────────────────
    @GET("store-mobile/inventory")
    suspend fun getInventory(
        @Query("store_id") storeId: String? = null,
        @Query("search") search: String? = null,
        @Query("only_low") onlyLow: Boolean? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30
    ): Response<ApiResponse<List<InventoryRow>>>

    // ── Store Mobile: Lookups ─────────────────────────────────────────
    @GET("store-mobile/lookup/materials")
    suspend fun getMaterials(
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<ApiResponse<List<Material>>>

    @GET("store-mobile/lookup/suppliers")
    suspend fun getSuppliers(
        @Query("search") search: String? = null
    ): Response<ApiResponse<List<Supplier>>>

    @GET("store-mobile/lookup/stores")
    suspend fun getStores(
        @Query("search") search: String? = null
    ): Response<ApiResponse<List<StoreInfo>>>

    @GET("store-mobile/lookup/departments")
    suspend fun getDepartments(
        @Query("search") search: String? = null
    ): Response<ApiResponse<List<Department>>>

    @GET("store-mobile/lookup/employees")
    suspend fun getEmployees(
        @Query("search") search: String? = null,
        @Query("department_id") departmentId: String? = null
    ): Response<ApiResponse<List<Employee>>>

    // ── Store Mobile: Goods Issue ─────────────────────────────────────────
    @GET("store-mobile/issues")
    suspend fun getIssues(
        @Query("store_id") storeId: String? = null,
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<ApiResponse<List<Issue>>>

    @GET("store-mobile/issues/{id}")
    suspend fun getIssue(@Path("id") id: String): Response<ApiResponse<Issue>>

    @POST("store-mobile/issues")
    suspend fun createIssue(@Body request: CreateIssueRequest): Response<ApiResponse<Issue>>

    @PATCH("store-mobile/issues/{id}/transition")
    suspend fun transitionIssue(
        @Path("id") id: String,
        @Body request: TransitionRequest
    ): Response<ApiResponse<Issue>>

    // ── Store Mobile: Stock Transfer ─────────────────────────────────
    @GET("store-mobile/transfers")
    suspend fun getTransfers(
        @Query("store_id") storeId: String? = null,
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<ApiResponse<List<Transfer>>>

    @GET("store-mobile/transfers/{id}")
    suspend fun getTransfer(@Path("id") id: String): Response<ApiResponse<Transfer>>

    @POST("store-mobile/transfers")
    suspend fun createTransfer(@Body request: CreateTransferRequest): Response<ApiResponse<Transfer>>

    @PATCH("store-mobile/transfers/{id}/transition")
    suspend fun transitionTransfer(
        @Path("id") id: String,
        @Body request: TransitionRequest
    ): Response<ApiResponse<Transfer>>

    // ── Store Mobile: Stock Verification ─────────────────────────────
    @GET("store-mobile/verifications")
    suspend fun getVerifications(
        @Query("store_id") storeId: String? = null,
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<ApiResponse<List<Verification>>>

    @GET("store-mobile/verifications/{id}")
    suspend fun getVerification(@Path("id") id: String): Response<ApiResponse<Verification>>

    @POST("store-mobile/verifications")
    suspend fun createVerification(
        @Body request: CreateVerificationRequest
    ): Response<ApiResponse<Verification>>

    @PATCH("store-mobile/verifications/{id}/transition")
    suspend fun transitionVerification(
        @Path("id") id: String,
        @Body request: TransitionRequest
    ): Response<ApiResponse<Verification>>

    /**
     * Upload a photo blob and get back a durable OSS path + a signed URL.
     * The form field name `file` is required (matches multer.single('file')).
     * `entity_code` is optional; defaults to "verification" server-side.
     */
    @Multipart
    @POST("store-mobile/upload-photo")
    suspend fun uploadPhoto(
        @Part file: MultipartBody.Part,
        @Part entityCode: MultipartBody.Part? = null
    ): Response<ApiResponse<UploadedPhoto>>
}
