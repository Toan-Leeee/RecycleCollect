package vn.edu.recyclecollect

import org.junit.Assert.*
import org.junit.Test

class DemoRepositoryTest {
    @Test fun oneCollectorAndOnlyOwnerCanComplete() {
        val repo = DemoRepository()
        val resident = repo.users.first { it.role == Role.RESIDENT }
        val collector = repo.users.first { it.role == Role.COLLECTOR }
        val other = repo.register("Khác", "khac@demo.vn", "demo123", Role.COLLECTOR)
        val r = repo.request("TG-001")
        repo.accept(collector, r)
        assertThrows(IllegalArgumentException::class.java) { repo.accept(other, r) }
        assertThrows(IllegalArgumentException::class.java) { repo.cancel(resident, r) }
        assertThrows(IllegalArgumentException::class.java) { repo.confirm(resident, r) }
        repo.submit(collector, r, "4.5")
        assertEquals(13500L, r.total)
        assertThrows(IllegalArgumentException::class.java) { repo.confirm(collector, r) }
        repo.confirm(resident, r)
        assertEquals(Status.DONE, r.status)
        assertFalse(repo.mayChat(resident, r))
        repo.saveMaterial(repo.users.first { it.role == Role.ADMIN }, "paper", "Giấy / carton", 9000)
        assertEquals(13500L, r.total)
    }
    @Test fun roleAndNumberValidation() {
        val repo = DemoRepository()
        val collector = repo.users.first { it.role == Role.COLLECTOR }
        val resident = repo.users.first { it.role == Role.RESIDENT }
        assertThrows(IllegalArgumentException::class.java) { repo.register("X", "x@demo.vn", "demo123", Role.ADMIN) }
        assertThrows(IllegalArgumentException::class.java) { repo.saveMaterial(resident, "paper", "Giấy", 1000) }
        assertThrows(IllegalArgumentException::class.java) { repo.submit(collector, repo.request("TG-002"), "NaN") }
        assertThrows(IllegalArgumentException::class.java) { repo.submit(collector, repo.request("TG-002"), "-1") }
        assertEquals(1502, DemoRepository.money(0.5005, 3000))
        assertNull(repo.login("dan@demo.vn", "wrong-password"))
        assertEquals(Role.RESIDENT, repo.login("DAN@DEMO.VN", "demo123")?.role)
    }
}
