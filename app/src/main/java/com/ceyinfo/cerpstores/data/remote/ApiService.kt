package com.ceyinfo.cerpstores.data.remote

import com.ceyinfo.cerpstores.data.model.ApiResponse
import com.ceyinfo.cerpstores.data.model.CreateGrnRequest
import com.ceyinfo.cerpstores.data.model.DashboardStats
import com.ceyinfo.cerpstores.data.model.Grn
import com.ceyinfo.cerpstores.data.model.LoginData
import com.ceyinfo.cerpstores.data.model.LoginRequest
import com.ceyinfo.cerpstores.data.model.Material
import com.ceyinfo.cerpstores.data.model.MyPermissionsData
import com.ceyinfo.cerpstores.data.model.MyRoleData
import com.ceyinfo.cerpstores.data.model.MyStore
import com.ceyinfo.cerpstores.data.model.Supplier
import com.ceyinfo.cerpstores.data.model.VerifyModuleData
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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
}
