package vn.edu.recyclecollect

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Patterns
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.activity.OnBackPressedCallback
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.*

class MainActivity : AppCompatActivity() {
    private lateinit var vm: AppViewModel
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var navigation: com.google.android.material.bottomnavigation.BottomNavigationView
    private val ink = Color.rgb(25, 54, 43)
    private val muted = Color.rgb(99, 116, 107)
    private val green = Color.rgb(22, 107, 80)
    private val lineColor = Color.rgb(218, 228, 221)
    private val white = Color.WHITE
    private val handler = Handler(Looper.getMainLooper())
    private var pendingLocation: Boolean? = null
    private var locationListener: LocationListener? = null
    private var locationTimeout: Runnable? = null
    private var navigating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this)[AppViewModel::class.java]
        setContentView(R.layout.activity_main)
        content = findViewById(R.id.content); scroll = findViewById(R.id.scroll)
        navigation = findViewById(R.id.navigation)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, max(bars.bottom, keyboard.bottom))
            insets
        }
        findViewById<ImageButton>(R.id.back).setOnClickListener { goBack() }
        findViewById<ImageButton>(R.id.menu).setOnClickListener { menu(it) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goBack() }
        })
        render()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun background(fill: Int = white, border: Int? = null, radius: Int = 8) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat(); border?.let { setStroke(dp(1), it) }
    }
    private fun gap(parent: LinearLayout = content, height: Int = 16) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height))) }
    private fun label(value: String, size: Float = 15f, bold: Boolean = false, color: Int = ink): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(dp(2).toFloat(), 1f)
    }
    private fun text(value: String, size: Float = 15f, bold: Boolean = false, color: Int = ink, parent: LinearLayout = content): TextView {
        val v = label(value, size, bold, color); parent.addView(v); return v
    }
    private fun heading(title: String, sub: String? = null) {
        text(title, 26f, true); if (sub != null) { gap(height = 6); text(sub, 14f, color = muted) }; gap(height = 24)
    }
    private fun section(title: String) { gap(height = 10); text(title, 18f, true); gap(height = 14) }
    private fun divider(parent: LinearLayout = content) { parent.addView(View(this).apply { setBackgroundColor(lineColor) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(15); bottomMargin = dp(15) }) }
    private fun button(title: String, outline: Boolean = false, parent: LinearLayout = content, action: () -> Unit): MaterialButton {
        val b = MaterialButton(this, null, if (outline) com.google.android.material.R.attr.materialButtonOutlinedStyle else com.google.android.material.R.attr.materialButtonStyle).apply {
            text = title; isAllCaps = false; textSize = 15f; letterSpacing = 0f; cornerRadius = dp(8); minHeight = dp(50)
            setTextColor(if (outline) green else white)
            if (!outline) backgroundTintList = android.content.res.ColorStateList.valueOf(green)
            insetTop = 0; insetBottom = 0
            setOnClickListener { action() }
        }
        parent.addView(b, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7); bottomMargin = dp(5) })
        return b
    }
    private fun link(title: String, action: () -> Unit) {
        content.addView(label(title, 14f, true, green).apply {
            gravity = Gravity.CENTER; minHeight = dp(48); setPadding(dp(8), dp(10), dp(8), dp(10)); setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, -2))
    }
    private fun input(name: String, value: String = "", type: Int = InputType.TYPE_CLASS_TEXT, parent: LinearLayout = content, changed: ((String) -> Unit)? = null): TextInputEditText {
        val wrapper = TextInputLayout(this).apply {
            hint = name; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE; setBoxCornerRadii(dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat())
            boxStrokeColor = green; boxBackgroundColor = white; defaultHintTextColor = android.content.res.ColorStateList.valueOf(muted)
            if (type and InputType.TYPE_TEXT_VARIATION_PASSWORD != 0 && type and InputType.TYPE_CLASS_NUMBER == 0) endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val edit = TextInputEditText(wrapper.context).apply { inputType = type; setText(value); background = null; textSize = 15f; setTextColor(ink); setSingleLine(true); setPadding(dp(14), dp(16), dp(14), dp(16)) }
        wrapper.addView(edit, LinearLayout.LayoutParams(-1, -2))
        parent.addView(wrapper, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5); bottomMargin = dp(11) })
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { wrapper.error = null; changed?.invoke(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        return edit
    }
    private fun error(edit: TextInputEditText, message: String): Boolean { (edit.parent.parent as? TextInputLayout)?.error = message; edit.requestFocus(); return false }
    private fun values(edit: EditText) = edit.text.toString().trim()
    private fun notify(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    private fun attempt(action: () -> Unit) { try { action() } catch (e: IllegalArgumentException) { notify(e.message ?: "Dữ liệu chưa hợp lệ.") } }
    private fun confirm(title: String, message: String, action: () -> Unit) {
        MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message).setNegativeButton("Quay lại", null).setPositiveButton("Xác nhận") { _, _ -> attempt(action) }.show()
    }
    private fun money(value: Long) = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(value) + " đ"
    private fun weight(value: Double) = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).apply { maximumFractionDigits = 2 }.format(value) + " kg"
    private fun row(name: String, value: String) {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, dp(12)) }
        r.addView(label(name, 14f, color = muted), LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(label(value, 15f, true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, -2, 1.2f))
        content.addView(r)
    }
    private fun chip(value: String, parent: LinearLayout = content, state: Status = Status.WAITING) {
        val color = when (state) { Status.WAITING -> Color.rgb(143, 96, 20); Status.CANCELLED -> Color.rgb(157, 60, 55); else -> green }
        parent.addView(label(value, 12f, true, color).apply { setPadding(dp(10), dp(5), dp(10), dp(5)); background = background(if (state == Status.WAITING) Color.rgb(255, 242, 215) else Color.rgb(229, 240, 234), radius = 5) }, LinearLayout.LayoutParams(-2, -2))
    }
    private fun select(name: String, items: List<String>, selected: Int, changed: (Int) -> Unit) {
        text(name, 13f, true, muted); gap(height = 7)
        val spinner = Spinner(this).apply {
            background = background(white, lineColor); minimumHeight = dp(52)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
            setSelection(selected.coerceAtLeast(0))
        }
        content.addView(spinner, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(16) })
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { changed(position) }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    private fun segments(items: List<String>, selected: Int, changed: (Int) -> Unit) {
        val container = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; background = background(Color.rgb(232, 238, 234)); setPadding(dp(3), dp(3), dp(3), dp(3)) }
        items.forEachIndexed { i, value ->
            val v = label(value, 12f, i == selected, if (i == selected) green else muted).apply {
                gravity = Gravity.CENTER; minHeight = dp(44); setPadding(dp(3), dp(5), dp(3), dp(5)); if (i == selected) background = background(white, radius = 6); setOnClickListener { changed(i) }
            }
            container.addView(v, LinearLayout.LayoutParams(0, -1, 1f))
        }
        content.addView(container, LinearLayout.LayoutParams(-1, dp(50))); gap(height = 18)
    }
    private fun go(screen: Screen, root: Boolean = false) {
        if (navigating) return
        navigating = true
        if (vm.screen == Screen.FORM && vm.formDirty && screen != Screen.FORM) {
            confirm("Rời biểu mẫu?", "Nội dung chưa lưu sẽ được giữ tạm trong lần chạy này.") { vm.navigate(screen, root); render() }
            navigating = false; return
        }
        vm.navigate(screen, root); render(); navigating = false
    }
    private fun goBack() {
        if (vm.screen == Screen.FORM && vm.formDirty) {
            confirm("Quay lại?", "Nội dung biểu mẫu được giữ tạm, chưa tạo yêu cầu.") { vm.back(); render() }
        } else { vm.back(); render() }
    }
    private fun openRequest(r: Request) { vm.selectedId = r.id; vm.chatDraft = ""; go(Screen.DETAIL) }
    private fun newRequest() { vm.editRequestId = null; vm.draft = Draft(); vm.formDirty = false; go(Screen.FORM) }

    private fun render() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(content.windowToken, 0)
        if (vm.screen !in listOf(Screen.LOGIN, Screen.REGISTER) && vm.user == null) vm.navigate(Screen.LOGIN, true)
        content.removeAllViews()
        findViewById<TextView>(R.id.title).text = if (vm.screen == Screen.FORM && vm.editRequestId != null) "Chỉnh sửa yêu cầu" else vm.screen.title
        findViewById<View>(R.id.back).visibility = if (vm.screen in listOf(Screen.HOME, Screen.LOGIN)) View.INVISIBLE else View.VISIBLE
        setupNavigation()
        when(vm.screen) {
            Screen.LOGIN -> login(); Screen.REGISTER -> register(); Screen.HOME -> home(); Screen.PROFILE -> profile()
            Screen.CATALOG -> catalog(false); Screen.FORM -> form(); Screen.REQUESTS -> requests()
            Screen.DETAIL -> detail(); Screen.EXPLORE -> explore(); Screen.MAP -> mapScreen(); Screen.CHAT -> chat()
            Screen.RESULT -> result(); Screen.HISTORY -> history(); Screen.ADMIN_MATERIALS -> catalog(true); Screen.MATERIAL_EDIT -> materialEditor()
        }
        scroll.post { scroll.scrollTo(0, 0) }
    }
    private fun setupNavigation() {
        navigation.setOnItemSelectedListener(null)
        navigation.menu.clear()
        val user = vm.user
        navigation.visibility = if (user == null) View.GONE else View.VISIBLE
        if (user == null) return
        val admin = user.role == Role.ADMIN
        val entries = if (admin) listOf(Triple("Trang chủ", android.R.drawable.ic_menu_view, Screen.HOME), Triple("Vật liệu", android.R.drawable.ic_menu_agenda, Screen.ADMIN_MATERIALS), Triple("Hồ sơ", android.R.drawable.ic_menu_myplaces, Screen.PROFILE))
        else listOf(Triple("Trang chủ", android.R.drawable.ic_menu_view, Screen.HOME), Triple(if(user.role==Role.RESIDENT) "Yêu cầu" else "Đơn nhận", android.R.drawable.ic_menu_agenda, Screen.REQUESTS), Triple("Lịch sử", android.R.drawable.ic_menu_recent_history, Screen.HISTORY), Triple("Hồ sơ", android.R.drawable.ic_menu_myplaces, Screen.PROFILE))
        entries.forEachIndexed { i, v -> navigation.menu.add(0, i + 1, i, v.first).setIcon(v.second) }
        navigation.labelVisibilityMode = com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED
        val active = when(vm.screen) {
            Screen.PROFILE -> entries.lastIndex
            Screen.ADMIN_MATERIALS, Screen.MATERIAL_EDIT -> 1
            Screen.REQUESTS, Screen.DETAIL, Screen.CHAT, Screen.RESULT, Screen.FORM -> 1
            Screen.HISTORY -> 2
            else -> 0
        }
        navigation.selectedItemId = active + 1
        navigation.setOnItemSelectedListener { item -> go(entries[item.itemId-1].third, true); true }
    }
    private fun menu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add("Về bản giao diện").setOnMenuItemClickListener {
            MaterialAlertDialogBuilder(this).setTitle("Thu gom tái chế · Bản giao diện")
                .setMessage("15 màn hình theo Chương 3. Dữ liệu và tài khoản chỉ mô phỏng trong bộ nhớ, được khôi phục khi khởi động lại tiến trình. Chưa kết nối Firebase, chưa xác thực thật. Bản đồ là hình minh họa tương tác. Camera và GPS dùng thiết bị nếu được cấp quyền.")
                .setPositiveButton("Đã hiểu", null).show(); true
        }
        if (vm.user != null) {
            Role.entries.forEach { role -> menu.menu.add("Xem thử: ${role.label}").setOnMenuItemClickListener { vm.login(vm.repo.users.first { it.role == role }); render(); true } }
            menu.menu.add(if(vm.showEmpty) "Tắt trạng thái trống" else "Xem trạng thái trống").setOnMenuItemClickListener { vm.showEmpty = !vm.showEmpty; render(); true }
            menu.menu.add(if(vm.showError) "Tắt lỗi minh họa" else "Xem lỗi tải minh họa").setOnMenuItemClickListener { vm.showError = !vm.showError; render(); true }
        }
        menu.show()
    }
    private fun login() {
        val logo = ImageView(this).apply { setImageResource(R.drawable.ic_recycle); contentDescription = "Biểu tượng thu gom tái chế" }
        content.addView(logo, LinearLayout.LayoutParams(dp(72), dp(72)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(16); bottomMargin = dp(24) })
        heading("Thu gom tái chế", "Kết nối người dân và người thu gom")
        val email = input("Email", vm.authEmail, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, changed = { vm.authEmail=it })
        val pass = input("Mật khẩu", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        button("Đăng nhập") {
            if (!Patterns.EMAIL_ADDRESS.matcher(values(email)).matches()) { error(email,"Email chưa hợp lệ"); return@button }
            if (values(pass).isEmpty()) { error(pass,"Nhập mật khẩu"); return@button }
            val user=vm.repo.login(values(email),values(pass))
            pass.text?.clear()
            if(user==null) error(pass,"Tài khoản minh họa hoặc mật khẩu không đúng") else {vm.login(user);render()}
        }
        link("Chưa có tài khoản? Đăng ký ngay") { go(Screen.REGISTER) }
        divider(); text("Tài khoản xem thử",14f,true); gap(height=5)
        text("Mật khẩu chung: demo123",13f,color=muted)
        val demo = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        listOf("Người dân" to "dan@demo.vn","Thu gom" to "gom@demo.vn","Admin" to "admin@demo.vn").forEach { (name,address) ->
            demo.addView(label(name,13f,true,green).apply { gravity=Gravity.CENTER; minHeight=dp(48); setOnClickListener { email.setText(address); pass.setText("demo123") } },LinearLayout.LayoutParams(0,-2,1f))
        };content.addView(demo)
    }
    private fun register() {
        heading("Tạo tài khoản", "Đăng ký mô phỏng trong bản giao diện")
        val name=input("Họ và tên",vm.registerName,changed={vm.registerName=it})
        val email=input("Email",vm.registerEmail,InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,changed={vm.registerEmail=it})
        val pass=input("Mật khẩu",type=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val again=input("Xác nhận mật khẩu",type=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        text("Vai trò",13f,true,muted);gap(height=8)
        val choices=RadioGroup(this).apply { orientation=RadioGroup.HORIZONTAL }
        listOf(Role.RESIDENT,Role.COLLECTOR).forEachIndexed { i,role -> choices.addView(RadioButton(this).apply { id=i+100;text=role.label;textSize=14f;isChecked=vm.registerRole==role },LinearLayout.LayoutParams(0,dp(48),1f)) }
        choices.setOnCheckedChangeListener { _,id -> vm.registerRole=if(id==100)Role.RESIDENT else Role.COLLECTOR };content.addView(choices);gap()
        button("Đăng ký mô phỏng") {
            if(values(name).isEmpty()){error(name,"Nhập họ và tên");return@button}
            if(!Patterns.EMAIL_ADDRESS.matcher(values(email)).matches()){error(email,"Email chưa hợp lệ");return@button}
            if(pass.text.toString().length<6){error(pass,"Mật khẩu tối thiểu 6 ký tự");return@button}
            if(pass.text.toString()!=again.text.toString()){error(again,"Mật khẩu xác nhận chưa khớp");return@button}
            attempt { vm.repo.register(values(name),values(email),pass.text.toString(),vm.registerRole);vm.authEmail=values(email);notify("Đã tạo tài khoản mô phỏng, chưa gửi tới Firebase.");go(Screen.LOGIN,true) }
        }
        link("Đã có tài khoản? Đăng nhập") { go(Screen.LOGIN,true) }
    }
    private fun dataState(): Boolean {
        if(vm.showError){gap(height=24);heading("Không tải được dữ liệu","Trạng thái lỗi minh họa, không phải lỗi mạng thực tế.");button("Thử lại"){vm.showError=false;render()};return false}
        return true
    }
    private fun empty(message: String) { gap(height=32);text(message,19f,true);gap(height=8);text("Chưa có mục nào trong danh sách này.",14f,color=muted);gap(height=28) }
    private fun home() {
        val user=vm.user!!
        text("Xin chào,",16f,color=muted);gap(height=4);heading(user.name,user.role.label)
        if(user.role==Role.ADMIN){button("Quản lý danh mục và giá"){go(Screen.ADMIN_MATERIALS)};section("Quản trị danh mục");text("${vm.repo.materials.size} loại vật liệu",23f,true);gap();text("Đơn giá tham khảo",14f,color=muted);vm.repo.materials.take(3).forEach { row(it.name,money(it.price)+"/kg") };return}
        if(user.role==Role.RESIDENT) button("＋  Tạo yêu cầu thu gom"){newRequest()} else button("Tìm yêu cầu chờ nhận"){go(Screen.EXPLORE)}
        button("Danh mục và bảng giá",true){go(Screen.CATALOG)}
        section(if(user.role==Role.RESIDENT)"Yêu cầu gần đây" else "Đơn đã nhận")
        if(!dataState())return
        val entries=if(vm.showEmpty)emptyList() else vm.repo.related(user).take(3)
        if(entries.isEmpty())empty("Chưa có yêu cầu") else entries.forEach { requestCard(it) }
        link("Xem tất cả yêu cầu") { go(Screen.REQUESTS) }
    }
    private fun profile() {
        val user=vm.user!!;heading(user.name,user.role.label)
        val draft=vm.profileDraft?:Triple(user.name,user.phone,user.address)
        text(user.email,14f,color=muted);gap()
        lateinit var name: TextInputEditText;lateinit var phone: TextInputEditText;lateinit var address: TextInputEditText
        fun capture(){vm.profileDraft=Triple(values(name),values(phone),values(address))}
        name=input("Họ và tên",draft.first);phone=input("Số điện thoại",draft.second,InputType.TYPE_CLASS_PHONE);address=input("Địa chỉ",draft.third)
        for(v in listOf(name,phone,address))v.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){capture()};override fun afterTextChanged(s:Editable?){} })
        button("Lưu thay đổi") {
            if(values(name).isEmpty()){error(name,"Họ và tên không được để trống");return@button}
            if(values(phone).isNotEmpty()&&!values(phone).matches(Regex("\\+?[0-9 ]{9,15}"))){error(phone,"Số điện thoại chưa hợp lệ");return@button}
            user.name=values(name);user.phone=values(phone);user.address=values(address);vm.profileDraft=null;notify("Đã lưu hồ sơ trong bản giao diện.");render()
        }
        gap();button("Đăng xuất",true){confirm("Đăng xuất?","Kết thúc phiên xem thử trên thiết bị này."){vm.logout();render()}}
    }
    private fun catalog(admin: Boolean) {
        val user=vm.user!!
        if(admin&&user.role!=Role.ADMIN){go(Screen.HOME,true);return}
        heading(if(admin)"Danh mục vật liệu" else "Bảng giá tham khảo",if(admin)"Quản lý tên vật liệu và đơn giá" else "Giá minh họa · Đơn vị đồng / kg")
        if(admin)button("＋  Thêm vật liệu"){vm.editMaterialId=null;vm.materialDraft=null;go(Screen.MATERIAL_EDIT)}
        if(!dataState())return
        if(vm.showEmpty){empty("Chưa có vật liệu");return}
        vm.repo.materials.forEach { material ->
            val item=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(16),0,dp(16)) }
            item.addView(MaterialArt(material.id),LinearLayout.LayoutParams(dp(55),dp(55)))
            val names=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(13),0,dp(8),0)}
            names.addView(label(material.name,16f,true));names.addView(label(money(material.price)+" / kg",14f,color=muted));item.addView(names,LinearLayout.LayoutParams(0,-2,1f))
            if(admin){item.addView(label("Sửa  ›",14f,true,green));item.setOnClickListener {vm.editMaterialId=material.id;vm.materialDraft=null;go(Screen.MATERIAL_EDIT)}}
            content.addView(item);content.addView(View(this).apply {setBackgroundColor(lineColor)},LinearLayout.LayoutParams(-1,dp(1)))
        }
        if(!admin&&user.role==Role.RESIDENT){gap();button("Tạo yêu cầu thu gom"){newRequest()}}
    }
    private fun materialEditor() {
        val user=vm.user!!;if(user.role!=Role.ADMIN){go(Screen.HOME,true);return}
        val current=vm.editMaterialId?.let(vm.repo::material)
        heading(if(current==null)"Thêm vật liệu" else "Cập nhật vật liệu")
        val initial=vm.materialDraft?:Pair(current?.name?:"",current?.price?.toString()?:"")
        val name=input("Tên vật liệu",initial.first);val price=input("Đơn giá tham khảo (đ/kg)",initial.second,InputType.TYPE_CLASS_NUMBER)
        fun capture(){vm.materialDraft=Pair(values(name),values(price))}
        for(v in listOf(name,price))v.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){capture()};override fun afterTextChanged(s:Editable?){} })
        gap();button("Lưu thay đổi"){
            if(values(name).isEmpty()){error(name,"Nhập tên vật liệu");return@button}
            val number=values(price).toLongOrNull();if(number==null||number !in 0..100000000){error(price,"Đơn giá phải từ 0 đến 100.000.000");return@button}
            attempt{vm.repo.saveMaterial(user,current?.id,values(name),number);vm.materialDraft=null;notify("Đã cập nhật dữ liệu minh họa.");go(Screen.ADMIN_MATERIALS,true)}
        }
        button("Hủy",true){confirm("Bỏ thay đổi?","Danh mục sẽ không được cập nhật."){vm.materialDraft=null;go(Screen.ADMIN_MATERIALS,true)}}
    }
    private fun form() {
        if(vm.user!!.role!=Role.RESIDENT){go(Screen.HOME,true);return}
        heading(if(vm.editRequestId==null)"Tạo yêu cầu" else "Chỉnh sửa yêu cầu","Thông tin vật liệu và địa điểm thu gom")
        val materials=vm.repo.materials
        select("Loại vật liệu",materials.map{it.name},materials.indexOfFirst{it.id==vm.draft.materialId}) { vm.draft.materialId=materials[it].id }
        input("Khối lượng dự kiến (kg)",vm.draft.weight,InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,changed={vm.draft.weight=it.replace(',','.');vm.formDirty=true})
        val photo=vm.draft.photo
        if(photo!=null)photoView(photo,vm.draft.materialId,180) else content.addView(LinearLayout(this).apply {
            gravity=Gravity.CENTER;orientation=LinearLayout.VERTICAL;background=background(white,lineColor)
            addView(ImageView(this@MainActivity).apply{setImageResource(android.R.drawable.ic_menu_camera);imageTintList=android.content.res.ColorStateList.valueOf(muted)},LinearLayout.LayoutParams(dp(40),dp(40)))
            addView(label("Chưa có ảnh vật liệu",14f,color=muted).apply {gravity=Gravity.CENTER})
        },LinearLayout.LayoutParams(-1,dp(150)))
        button(if(photo==null)"Chụp ảnh vật liệu" else "Chụp lại ảnh",true){camera()}
        link("Dùng ảnh mẫu để xem thử") { vm.draft.photo="demo";vm.formDirty=true;render() }
        section("Địa điểm thu gom")
        text(if(vm.draft.address.isEmpty())"Chưa lấy vị trí" else vm.draft.address,14f,color=muted)
        if(vm.draft.demoPosition){gap(height=5);text("Vị trí minh họa tại Cầu Giấy",12f,color=green)}
        button("Lấy vị trí GPS",true){location(true)}
        link("Dùng vị trí minh họa") { vm.draft.lat=21.0364;vm.draft.lon=105.7878;vm.draft.address="Cầu Giấy, Hà Nội (minh họa)";vm.draft.demoPosition=true;vm.formDirty=true;render() }
        gap();button(if(vm.editRequestId==null)"Gửi yêu cầu mô phỏng" else "Lưu thay đổi"){
            attempt {val r=vm.repo.save(vm.user!!,vm.draft,vm.editRequestId);vm.formDirty=false;vm.selectedId=r.id;vm.draft=Draft();vm.editRequestId=null;notify("Đã lưu yêu cầu trong dữ liệu minh họa.");go(Screen.DETAIL,true)}
        }
    }
    private fun photoView(photo: String?, type: String, height: Int) {
        if(photo!=null&&photo!="demo"&&File(photo).exists())content.addView(ImageView(this).apply{setImageBitmap(BitmapFactory.decodeFile(photo));scaleType=ImageView.ScaleType.CENTER_CROP;contentDescription="Ảnh vật liệu đã chụp";background=background(white,lineColor)},LinearLayout.LayoutParams(-1,dp(height)))
        else content.addView(MaterialArt(type,true),LinearLayout.LayoutParams(-1,dp(height)))
        gap(height=10)
    }
    private fun requests() {
        val user=vm.user!!
        heading(if(user.role==Role.RESIDENT)"Yêu cầu của tôi" else "Đơn đã nhận","Theo dõi quá trình thu gom")
        val states=if(user.role==Role.RESIDENT) listOf(Status.WAITING,Status.ACCEPTED,Status.DONE,Status.CANCELLED) else listOf(Status.ACCEPTED,Status.DONE)
        if(vm.listStatus !in states)vm.listStatus=states.first()
        segments(states.map{it.label},states.indexOf(vm.listStatus)){vm.listStatus=states[it];render()}
        if(!dataState())return
        val entries=if(vm.showEmpty)emptyList() else vm.repo.related(user).filter{it.status==vm.listStatus}
        if(entries.isEmpty())empty("Chưa có đơn ${vm.listStatus.label.lowercase()}") else entries.forEach { requestCard(it) }
        if(user.role==Role.RESIDENT)button("＋  Tạo yêu cầu mới"){newRequest()}
        else button("Tìm thêm yêu cầu",true){go(Screen.EXPLORE)}
    }
    private fun requestCard(r: Request, distance: Boolean = false) {
        val box=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;background=background(white,lineColor);setPadding(dp(12),dp(13),dp(12),dp(13));setOnClickListener{openRequest(r)};contentDescription="${r.id} ${vm.repo.material(r.materialId).name}, ${r.status.label}"}
        if(r.photo!=null&&r.photo!="demo"&&File(r.photo!!).exists())box.addView(ImageView(this).apply {setImageBitmap(BitmapFactory.decodeFile(r.photo));scaleType=ImageView.ScaleType.CENTER_CROP},LinearLayout.LayoutParams(dp(64),dp(76)))
        else box.addView(MaterialArt(r.materialId),LinearLayout.LayoutParams(dp(64),dp(76)))
        val column=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(12),0,0,0)}
        column.addView(label(vm.repo.material(r.materialId).name,16f,true));column.addView(label(r.id,11f,color=muted))
        column.addView(label(if(r.status==Status.DONE)"${weight(r.actual!!)} · ${money(r.total!!)}" else "${weight(r.estimate)}"+(if(distance)" · ${"%.1f".format(distance(r))} km" else ""),13f,color=muted),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(4);bottomMargin=dp(8)})
        chip(r.status.label,column,r.status);box.addView(column,LinearLayout.LayoutParams(0,-2,1f));box.addView(label("›",22f,color=muted))
        content.addView(box,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(12)})
    }
    private fun detail() {
        val r=vm.selected()?:return;val user=vm.user!!
        if(!vm.repo.mayRead(user,r)){empty("Bạn không được xem đơn này");return}
        if(!dataState())return
        photoView(r.photo,r.materialId,190);gap(height=7);heading(vm.repo.material(r.materialId).name,r.id)
        chip(if(r.actual!=null&&r.status==Status.ACCEPTED)"Chờ xác nhận kết quả" else r.status.label,state=r.status);gap()
        row("Khối lượng dự kiến",weight(r.estimate))
        val precise = r.owner==user.id||r.collector==user.id
        row("Địa điểm",if(precise)r.address else "Khu vực Cầu Giấy")
        if(r.collector!=null){row("Người thu gom",vm.repo.user(r.collector!!).name);row("Người dân",vm.repo.user(r.owner).name)}
        if(precise&&r.status!=Status.WAITING)row("Liên hệ",vm.repo.user(if(user.id==r.owner)r.collector?:r.owner else r.owner).phone.ifEmpty{"Chưa cập nhật"})
        divider()
        if(r.status==Status.WAITING&&r.owner==user.id){
            button("Chỉnh sửa yêu cầu") {vm.editRequestId=r.id;vm.draft=Draft(r.materialId,r.estimate.toString(),r.photo?:"demo",r.lat,r.lon,r.address,true);vm.formDirty=false;go(Screen.FORM)}
            button("Hủy yêu cầu",true){confirm("Hủy ${r.id}?","Chỉ có thể hủy khi đơn chưa được nhận."){vm.repo.cancel(user,r);notify("Đã hủy đơn trong dữ liệu minh họa.");render()}}
        }
        if(r.status==Status.WAITING&&user.role==Role.COLLECTOR)button("Nhận đơn thu gom"){confirm("Nhận ${r.id}?","Thông tin địa điểm và liên hệ được mở sau khi nhận."){vm.repo.accept(user,r);notify("Đã nhận đơn mô phỏng.");render()}}
        if(r.status==Status.ACCEPTED){
            button("Nhắn tin"){go(Screen.CHAT)}
            button(if(user.role==Role.COLLECTOR)"Nhập kết quả thu gom" else "Xem kết quả thu gom",true){go(Screen.RESULT)}
        }
        if(r.status==Status.DONE){row("Khối lượng thực tế",weight(r.actual!!));row("Đơn giá đã lưu",money(r.unitPrice!!)+"/kg");row("Tổng tiền",money(r.total!!));row("Hoàn tất",r.completedDate?:"");button("Đọc lại hội thoại",true){go(Screen.CHAT)}}
    }
    private fun distance(r: Request):Double {val result=FloatArray(1);Location.distanceBetween(vm.latitude,vm.longitude,r.lat,r.lon,result);return result[0]/1000.0}
    private fun nearby():List<Request> = if(vm.showEmpty)emptyList() else vm.repo.requests.filter {it.status==Status.WAITING&&(vm.materialFilter==null||it.materialId==vm.materialFilter)&&distance(it)<=vm.radius}
    private fun explore() {
        if(vm.user!!.role!=Role.COLLECTOR){go(Screen.HOME,true);return}
        heading("Thu gom gần bạn",if(vm.demoLocation)"Vị trí minh họa · Cầu Giấy, Hà Nội" else "Đang dùng vị trí GPS của thiết bị")
        val materials=vm.repo.materials
        select("Loại vật liệu",listOf("Tất cả vật liệu")+materials.map{it.name},if(vm.materialFilter==null)0 else materials.indexOfFirst{it.id==vm.materialFilter}+1){val id=if(it==0)null else materials[it-1].id;if(vm.materialFilter!=id){vm.materialFilter=id;render()}}
        val radiusText=text("Khoảng cách tối đa: ${vm.radius} km",15f,true)
        content.addView(SeekBar(this).apply {min=1;max=10;progress=vm.radius;setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(s:SeekBar?,progress:Int,fromUser:Boolean){vm.radius=progress;radiusText.text="Khoảng cách tối đa: $progress km"}
            override fun onStartTrackingTouch(s:SeekBar?){}
            override fun onStopTrackingTouch(s:SeekBar?){render()}
        })},LinearLayout.LayoutParams(-1,dp(48)))
        button("Xem bản đồ minh họa",true){go(Screen.MAP)}
        link("Lấy vị trí GPS của thiết bị"){location(false)}
        section("Yêu cầu chờ nhận")
        if(!dataState())return
        val entries=nearby();if(entries.isEmpty())empty("Không có yêu cầu phù hợp") else entries.forEach{requestCard(it,true)}
    }
    private fun mapScreen() {
        if(vm.user!!.role!=Role.COLLECTOR){go(Screen.HOME,true);return}
        heading("Bản đồ minh họa","${vm.materialFilter?.let(vm.repo::material)?.name?:"Tất cả vật liệu"} · Trong ${vm.radius} km")
        if(!dataState())return
        val entries=nearby()
        content.addView(DemoMap(entries),LinearLayout.LayoutParams(-1,dp(310)))
        gap(height=8);text("Sơ đồ vị trí mẫu, chưa kết nối Google Maps.",12f,color=muted)
        button("Vị trí của tôi",true){location(false)}
        if(entries.isEmpty())empty("Không có điểm phù hợp") else {section("Chọn yêu cầu");entries.forEach{requestCard(it,true)}}
    }
    private fun chat() {
        val r=vm.selected()?:return;val user=vm.user!!
        if(r.collector==null||(user.id!=r.owner&&user.id!=r.collector)){empty("Hội thoại chưa khả dụng");return}
        val partner=vm.repo.user(if(user.id==r.owner)r.collector!! else r.owner)
        heading(partner.name,r.id+" · Hội thoại minh họa")
        r.messages.forEach {m ->
            val own=m.sender==user.id
            val box=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;background=background(if(own)Color.rgb(221,238,227) else white);setPadding(dp(14),dp(11),dp(14),dp(11))}
            box.addView(label(m.text,15f));box.addView(label(m.time,11f,color=muted),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(6)})
            content.addView(box,LinearLayout.LayoutParams(-1,-2).apply{leftMargin=if(own)dp(35)else 0;rightMargin=if(own)0 else dp(35);bottomMargin=dp(14)})
        }
        gap()
        if(vm.repo.mayChat(user,r)){
            val edit=input("Nhập tin nhắn",vm.chatDraft,changed={vm.chatDraft=it})
            button("Gửi tin nhắn"){
                if(values(edit).isEmpty()){error(edit,"Nhập nội dung tin nhắn");return@button}
                r.messages.add(Message(user.id,values(edit),java.text.SimpleDateFormat("HH:mm",Locale.US).format(java.util.Date())));vm.chatDraft="";render();scroll.post{scroll.fullScroll(View.FOCUS_DOWN)}
            }
        }else{chip("Hội thoại chỉ đọc",state=r.status);gap();text("Đơn đã ${r.status.label.lowercase()}. Không thể gửi tin mới.",14f,color=muted)}
    }
    private fun result() {
        val r=vm.selected()?:return;val user=vm.user!!
        if((r.owner!=user.id&&r.collector!=user.id)||r.status !in listOf(Status.ACCEPTED,Status.DONE)){empty("Không có kết quả khả dụng");return}
        heading("Kết quả thu gom",r.id+" · "+vm.repo.material(r.materialId).name)
        val collector=user.id==r.collector&&r.status==Status.ACCEPTED
        val unit=if(r.actual!=null)r.unitPrice!! else vm.repo.material(r.materialId).price
        var edit: TextInputEditText?=null
        if(collector)edit=input("Khối lượng thực tế (kg)",vm.resultDrafts[r.id]?:r.actual?.toString()?:"",InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,changed={vm.resultDrafts[r.id]=it.replace(',','.')})
        else if(r.actual!=null)row("Khối lượng thực tế",weight(r.actual!!))
        else {empty("Chưa có kết quả thu gom");button("Trao đổi với người thu gom",true){go(Screen.CHAT)};return}
        row("Đơn giá theo bảng giá",money(unit)+"/kg");divider()
        text("Tổng tiền",14f,color=muted)
        val total=text(if(r.total==null)"—" else money(r.total!!),32f,true,green);gap()
        edit?.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun afterTextChanged(s:Editable?){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){val number=s.toString().replace(',','.').toDoubleOrNull();total.text=if(number!=null&&number.isFinite()&&number in 0.000001..100000.0)money(DemoRepository.money(number,vm.repo.material(r.materialId).price))else "—"} })
        if(collector){
            if(r.actual!=null){chip("Đã gửi · Chờ người dân xác nhận");gap()}
            button(if(r.actual==null)"Gửi kết quả thu gom" else "Cập nhật kết quả"){attempt{vm.repo.submit(user,r,values(edit!!).replace(',','.'));vm.resultDrafts.remove(r.id);notify("Đã gửi kết quả mô phỏng. Chuyển sang Người dân để xác nhận.");render()}}
        }else if(r.status==Status.ACCEPTED){
            button("Xác nhận hoàn tất"){confirm("Xác nhận thu gom?","${weight(r.actual!!)} · ${money(r.total!!)}. Giao dịch sẽ được lưu vào lịch sử mô phỏng."){vm.repo.confirm(user,r);notify("Đã hoàn tất giao dịch mô phỏng.");go(Screen.DETAIL,true)}}
        }else chip("Giao dịch đã hoàn tất",state=Status.DONE)
        button("Trao đổi qua tin nhắn",true){go(Screen.CHAT)}
    }
    private fun history() {
        val user=vm.user!!;heading("Lịch sử thu gom","Giao dịch đã hoàn tất của bạn")
        if(!dataState())return
        val entries=if(vm.showEmpty)emptyList() else vm.repo.related(user).filter{it.status==Status.DONE}
        text("TỔNG KHỐI LƯỢNG",12f,true,muted);gap(height=4);text(weight(entries.sumOf{it.actual?:0.0}),32f,true,green)
        divider();text("TỔNG TIỀN",12f,true,muted);gap(height=4);text(money(entries.sumOf{it.total?:0L}),32f,true,ink)
        section("${entries.size} giao dịch hoàn tất")
        if(entries.isEmpty())empty("Chưa có giao dịch")else entries.forEach {requestCard(it)}
    }

    private fun camera() {
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.CAMERA),101);return}
        try{startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE),201)}catch(e:Exception){notify("Không mở được camera trên thiết bị. Có thể dùng ảnh mẫu để xem giao diện.")}
    }
    @Deprecated("Deprecated in Java") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==201&&resultCode==Activity.RESULT_OK){
            val bitmap=data?.extras?.get("data") as? Bitmap
            if(bitmap==null){notify("Camera chưa trả ảnh. Hãy thử lại.");return}
            val file=File(filesDir,"photo-${System.currentTimeMillis()}.jpg")
            file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)}
            vm.draft.photo=file.absolutePath;vm.formDirty=true;render()
        }
    }
    private fun stopLocation(){
        val manager=getSystemService(LOCATION_SERVICE) as LocationManager
        locationListener?.let{manager.removeUpdates(it)};locationListener=null
        locationTimeout?.let{handler.removeCallbacks(it)};locationTimeout=null
    }
    private fun location(form:Boolean){
        pendingLocation=form
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),102);return}
        stopLocation()
        val manager=getSystemService(LOCATION_SERVICE) as LocationManager
        val providers=listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER).filter{manager.isProviderEnabled(it)}
        if(providers.isEmpty()){notify("Hãy bật vị trí thiết bị, rồi thử lại.");return}
        fun use(position:Location){
            if(form){vm.draft.lat=position.latitude;vm.draft.lon=position.longitude;vm.draft.address="GPS: %.5f, %.5f".format(position.latitude,position.longitude);vm.draft.demoPosition=false;vm.formDirty=true}
            else {vm.latitude=position.latitude;vm.longitude=position.longitude;vm.demoLocation=false}
            stopLocation();notify("Đã lấy vị trí thiết bị, độ chính xác khoảng ${position.accuracy.toInt()} m.");render()
        }
        val recent=providers.mapNotNull{manager.getLastKnownLocation(it)}.filter{System.currentTimeMillis()-it.time<120000}.minByOrNull{it.accuracy}
        if(recent!=null){use(recent);return}
        notify("Đang lấy vị trí GPS…")
        locationListener=object:LocationListener{override fun onLocationChanged(position:Location){use(position)};override fun onProviderEnabled(provider:String){};override fun onProviderDisabled(provider:String){};@Deprecated("Deprecated in Java")override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?){} }
        providers.forEach{manager.requestLocationUpdates(it,1000L,0f,locationListener!!,Looper.getMainLooper())}
        locationTimeout=Runnable{stopLocation();notify("Chưa lấy được GPS. Hãy thử ngoài trời hoặc dùng vị trí minh họa.")}.also{handler.postDelayed(it,20000)}
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==101){if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)camera()else notify("Chưa được cấp quyền camera.")}
        if(requestCode==102){if(grantResults.any{it==PackageManager.PERMISSION_GRANTED})location(pendingLocation?:true)else notify("Chưa được cấp quyền vị trí.")}
    }
    override fun onDestroy(){stopLocation();super.onDestroy()}

    private inner class MaterialArt(private val kind:String,private val large:Boolean=false):View(this@MainActivity){
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        init{contentDescription="Hình minh họa vật liệu ${vm.repo.materials.find{it.id==kind}?.name?:kind}"}
        override fun onDraw(canvas:Canvas){
            super.onDraw(canvas);val w=width.toFloat();val h=height.toFloat();canvas.drawColor(if(kind=="paper")Color.rgb(248,237,218)else if(kind=="metal")Color.rgb(232,237,243)else Color.rgb(225,239,232))
            canvas.save();canvas.translate(w/2,h/2);val scale=min(w,h)/(if(large)100f else 80f);canvas.scale(scale,scale)
            paint.style=Paint.Style.FILL
            when(kind){
                "paper"->{paint.color=Color.rgb(191,145,93);canvas.drawRect(-26f,-19f,23f,24f,paint);paint.color=Color.rgb(215,174,122);canvas.drawRect(-18f,-28f,30f,15f,paint);paint.style=Paint.Style.STROKE;paint.strokeWidth=2f;paint.color=Color.rgb(134,99,60);canvas.drawLine(-18f,-7f,30f,-7f,paint);canvas.drawLine(5f,-28f,5f,15f,paint)}
                "metal"->{paint.color=Color.rgb(131,158,176);canvas.drawRoundRect(-18f,-26f,18f,29f,7f,7f,paint);paint.color=Color.rgb(197,213,223);canvas.drawOval(-18f,-29f,18f,-19f,paint);paint.style=Paint.Style.STROKE;paint.strokeWidth=2f;paint.color=Color.rgb(75,112,135);canvas.drawOval(-6f,-26f,7f,-22f,paint);canvas.drawLine(-10f,-10f,-10f,18f,paint)}
                "electronic"->{paint.color=Color.rgb(68,99,86);canvas.drawRoundRect(-20f,-30f,20f,30f,5f,5f,paint);paint.color=Color.rgb(176,206,184);canvas.drawRect(-15f,-23f,15f,18f,paint);paint.color=white;canvas.drawCircle(0f,24f,2f,paint)}
                else->{paint.color=if(kind=="glass")Color.rgb(84,127,110)else Color.rgb(121,177,166);canvas.drawRoundRect(-14f,-16f,14f,30f,6f,6f,paint);canvas.drawRect(-7f,-29f,7f,-13f,paint);paint.color=Color.rgb(49,101,83);canvas.drawRect(-8f,-33f,8f,-27f,paint);paint.color=Color.rgb(231,245,232);canvas.drawRect(-13f,1f,13f,17f,paint)}
            };canvas.restore()
        }
    }
    private inner class DemoMap(private val entries:List<Request>):View(this@MainActivity){
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        private val points=mutableListOf<Pair<PointF,Request>>()
        init{contentDescription="Sơ đồ minh họa các yêu cầu thu gom; chọn yêu cầu trong danh sách bên dưới."}
        override fun onDraw(c:Canvas){
            c.drawColor(Color.rgb(231,237,230));paint.style=Paint.Style.FILL;paint.color=Color.rgb(211,226,210)
            c.drawRect(width*.08f,height*.08f,width*.40f,height*.28f,paint);c.drawRect(width*.62f,height*.58f,width*.96f,height*.89f,paint)
            paint.color=white;paint.strokeWidth=dp(14).toFloat()
            for(i in 1..4)c.drawLine(0f,height*i/5f,width.toFloat(),height*(i+.5f)/5,paint)
            for(i in 1..3)c.drawLine(width*i/4f,0f,width*(i-.2f)/4,height.toFloat(),paint)
            paint.color=muted;paint.textSize=dp(11).toFloat();c.drawText("CẦU GIẤY",dp(15).toFloat(),height*.15f,paint);c.drawText("DUY TÂN",width*.5f,height*.83f,paint)
            points.clear();entries.forEachIndexed{i,r ->
                val x=width*(.25f+(i%3)*.24f);val y=height*(.38f+(i/3)*.21f)
                paint.color=green;c.drawCircle(x,y,dp(16).toFloat(),paint);paint.color=white;paint.textSize=dp(13).toFloat();paint.textAlign=Paint.Align.CENTER;c.drawText((i+1).toString(),x,y+dp(4),paint);paint.textAlign=Paint.Align.LEFT;points.add(PointF(x,y) to r)
            }
        }
        override fun onTouchEvent(event:MotionEvent):Boolean {if(event.action==MotionEvent.ACTION_UP){performClick();points.firstOrNull{hypot(event.x-it.first.x,event.y-it.first.y)<dp(28)}?.let{openRequest(it.second)};return true};return true}
        override fun performClick():Boolean{super.performClick();return true}
    }
}
