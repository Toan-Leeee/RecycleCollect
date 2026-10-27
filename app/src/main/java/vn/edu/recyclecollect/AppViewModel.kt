package vn.edu.recyclecollect

import androidx.lifecycle.ViewModel

enum class Screen(val title: String) {
    LOGIN("Đăng nhập"), REGISTER("Đăng ký"), HOME("Trang chủ"), PROFILE("Hồ sơ cá nhân"),
    CATALOG("Danh mục và bảng giá"), FORM("Tạo yêu cầu thu gom"), REQUESTS("Yêu cầu của tôi"),
    DETAIL("Chi tiết yêu cầu"), EXPLORE("Yêu cầu chờ nhận"), MAP("Bản đồ yêu cầu"),
    CHAT("Tin nhắn"), RESULT("Kết quả thu gom"), HISTORY("Lịch sử và thống kê"),
    ADMIN_MATERIALS("Quản lý vật liệu"), MATERIAL_EDIT("Thêm / sửa vật liệu")
}

class AppViewModel : ViewModel() {
    val repo = DemoRepository()
    var user: User? = null
    var screen = Screen.LOGIN
    val stack = mutableListOf<Screen>()
    var selectedId: String? = null
    var editRequestId: String? = null
    var editMaterialId: String? = null
    var draft = Draft()
    var listStatus = Status.WAITING
    var materialFilter: String? = null
    var radius = 5
    var latitude = 21.0285
    var longitude = 105.7900
    var demoLocation = true
    var showEmpty = false
    var showError = false
    var formDirty = false
    val resultDrafts = mutableMapOf<String, String>()
    var profileDraft: Triple<String, String, String>? = null
    var materialDraft: Pair<String, String>? = null
    var chatDraft = ""
    var authEmail = ""
    var registerName = ""
    var registerEmail = ""
    var registerRole = Role.RESIDENT
    fun navigate(next: Screen, root: Boolean = false) {
        if (root) stack.clear() else if (screen != next) stack.add(screen)
        screen = next
    }
    fun login(user: User) { this.user = user; showEmpty = false; showError = false; profileDraft = null; navigate(Screen.HOME, true) }
    fun logout() { user = null; draft = Draft(); formDirty = false; profileDraft = null; chatDraft = ""; navigate(Screen.LOGIN, true) }
    fun selected() = selectedId?.let(repo::request)
    fun back() { screen = if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) else if (user == null) Screen.LOGIN else Screen.HOME }
}
