package vn.edu.recyclecollect

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.util.UUID

enum class Role(val label: String) { RESIDENT("Người dân"), COLLECTOR("Người thu gom"), ADMIN("Admin") }
enum class Status(val label: String) { WAITING("Chờ nhận"), ACCEPTED("Đã nhận"), DONE("Hoàn tất"), CANCELLED("Đã hủy") }
data class User(val id: String, var name: String, val email: String, val role: Role, var phone: String = "", var address: String = "")
data class Material(val id: String, var name: String, var price: Long, val symbol: String)
data class Message(val sender: String, val text: String, val time: String)
data class Request(
    val id: String, val owner: String, var materialId: String, var estimate: Double,
    var address: String, var lat: Double, var lon: Double, var photo: String? = null,
    var status: Status = Status.WAITING, var collector: String? = null,
    var actual: Double? = null, var unitPrice: Long? = null, var total: Long? = null,
    var completedDate: String? = null, val messages: MutableList<Message> = mutableListOf()
)

/** In-memory fixture repository. It makes no network calls and is not authentication or a backend. */
class DemoRepository {
    val users = mutableListOf(
        User("resident", "Nguyễn Minh Anh", "dan@demo.vn", Role.RESIDENT, "0900000001", "Cầu Giấy, Hà Nội"),
        User("collector", "Trần Văn Bình", "gom@demo.vn", Role.COLLECTOR, "0900000002", "Đống Đa, Hà Nội"),
        User("admin", "Quản trị viên", "admin@demo.vn", Role.ADMIN)
    )
    private val credentials = users.associate { it.id to hash(it.id, "demo123") }.toMutableMap()
    val materials = mutableListOf(
        Material("paper", "Giấy / carton", 3000, "Giấy"), Material("plastic", "Nhựa PET", 5000, "Nhựa"),
        Material("metal", "Kim loại", 10000, "Lon"), Material("glass", "Thủy tinh", 1000, "Chai"),
        Material("electronic", "Điện tử", 15000, "Máy")
    )
    val requests = mutableListOf(
        Request("TG-001", "resident", "paper", 5.0, "12 Trần Thái Tông, Cầu Giấy", 21.0364, 105.7878),
        Request("TG-002", "resident", "plastic", 3.0, "26 Duy Tân, Cầu Giấy", 21.0310, 105.7831, status = Status.ACCEPTED, collector = "collector"),
        Request("TG-003", "resident", "metal", 2.0, "8 Trung Kính, Cầu Giấy", 21.0185, 105.7932, status = Status.DONE, collector = "collector", actual = 2.0, unitPrice = 10000, total = 20000, completedDate = "25/10/2026"),
        Request("TG-004", "resident", "paper", 4.5, "18 Thành Thái, Cầu Giấy", 21.0288, 105.7866, status = Status.DONE, collector = "collector", actual = 4.5, unitPrice = 3000, total = 13500, completedDate = "24/10/2026")
    )
    init {
        requests[1].messages.add(Message("collector", "Chào bạn, tôi có thể đến thu gom lúc 9 giờ không?", "08:15"))
        requests[1].messages.add(Message("resident", "Vâng, tôi đã phân loại và chuẩn bị sẵn.", "08:17"))
    }
    private fun hash(salt: String, password: String) = MessageDigest.getInstance("SHA-256").digest("$salt:$password".toByteArray()).joinToString("") { "%02x".format(it) }
    fun login(email: String, password: String): User? = users.find { it.email.equals(email.trim(), true) && credentials[it.id] == hash(it.id, password) }
    fun register(name: String, email: String, password: String, role: Role): User {
        require(role != Role.ADMIN) { "Không thể tự đăng ký Admin." }
        require(users.none { it.email.equals(email, true) }) { "Email đã tồn tại trong bản mô phỏng." }
        val user = User(UUID.randomUUID().toString(), name.trim(), email.trim(), role)
        users.add(user); credentials[user.id] = hash(user.id, password)
        return user
    }
    fun material(id: String) = materials.first { it.id == id }
    fun user(id: String) = users.first { it.id == id }
    fun request(id: String) = requests.first { it.id == id }
    fun related(user: User) = requests.filter { if (user.role == Role.COLLECTOR) it.collector == user.id else it.owner == user.id }
    fun mayRead(user: User, r: Request) = user.id == r.owner || user.id == r.collector || (user.role == Role.COLLECTOR && r.status == Status.WAITING)
    fun mayChat(user: User, r: Request) = (user.id == r.owner || user.id == r.collector) && r.status == Status.ACCEPTED
    fun save(user: User, draft: Draft, editId: String?): Request {
        require(user.role == Role.RESIDENT)
        require(draft.weight.toDoubleOrNull()?.let { it.isFinite() && it > 0 && it <= 100000 } == true) { "Khối lượng phải lớn hơn 0 và không vượt quá 100.000 kg." }
        require(draft.photo != null) { "Hãy chụp ảnh vật liệu." }
        require(draft.lat != null && draft.lon != null) { "Hãy lấy vị trí GPS hoặc chọn vị trí minh họa." }
        val existing = editId?.let(::request)
        require(existing == null || (existing.owner == user.id && existing.status == Status.WAITING)) { "Chỉ sửa yêu cầu của mình đang chờ nhận." }
        val r = existing ?: Request("TG-${(requests.size + 1).toString().padStart(3, '0')}", user.id, draft.materialId, draft.weight.toDouble(), draft.address, draft.lat!!, draft.lon!!)
        r.materialId = draft.materialId; r.estimate = draft.weight.toDouble(); r.photo = draft.photo
        r.address = draft.address; r.lat = draft.lat!!; r.lon = draft.lon!!
        if (existing == null) requests.add(0, r)
        return r
    }
    @Synchronized fun accept(user: User, r: Request) {
        require(user.role == Role.COLLECTOR && r.owner != user.id && r.status == Status.WAITING && r.collector == null) { "Đơn không còn chờ nhận." }
        r.collector = user.id; r.status = Status.ACCEPTED
    }
    fun cancel(user: User, r: Request) {
        require(r.owner == user.id && r.status == Status.WAITING) { "Không thể hủy đơn đã nhận." }; r.status = Status.CANCELLED
    }
    fun submit(user: User, r: Request, weight: String) {
        require(r.collector == user.id && r.status == Status.ACCEPTED)
        val value = weight.toDoubleOrNull()
        require(value != null && value.isFinite() && value > 0 && value <= 100000) { "Khối lượng thực tế không hợp lệ." }
        r.actual = value; r.unitPrice = material(r.materialId).price; r.total = money(value, r.unitPrice!!)
    }
    fun confirm(user: User, r: Request) {
        require(r.owner == user.id && r.status == Status.ACCEPTED && r.actual != null) { "Chưa có kết quả thu gom để xác nhận." }
        r.status = Status.DONE; r.completedDate = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(java.util.Date())
    }
    fun saveMaterial(user: User, id: String?, name: String, price: Long) {
        require(user.role == Role.ADMIN); require(name.trim().isNotEmpty() && price in 0..100000000) { "Tên hoặc đơn giá không hợp lệ." }
        require(materials.none { it.id != id && it.name.equals(name.trim(), true) }) { "Tên vật liệu đã tồn tại." }
        if (id == null) materials.add(Material(UUID.randomUUID().toString(), name.trim(), price, "Khác"))
        else material(id).apply { this.name = name.trim(); this.price = price }
    }
    companion object {
        fun money(weight: Double, unitPrice: Long): Long = BigDecimal.valueOf(weight).multiply(BigDecimal.valueOf(unitPrice)).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }
}

data class Draft(var materialId: String = "paper", var weight: String = "", var photo: String? = null, var lat: Double? = null, var lon: Double? = null, var address: String = "", var demoPosition: Boolean = false)
