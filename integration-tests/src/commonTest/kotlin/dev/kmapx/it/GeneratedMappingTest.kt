package dev.kmapx.it

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * El proyecto de integración consume la anotación y KSP genera código usable.
 * Si este archivo no compila, el pipeline anotación → plan → emisión está roto.
 */
class GeneratedMappingTest {

    @Test
    fun `la extension generada mapea correctamente`() {
        val dto = Person(name = "Ada", age = 36).toPersonDto()
        assertEquals(PersonDto(name = "Ada", age = 36), dto)
    }

    @Test
    fun `MapTo repeatable con name explicito genera la segunda extension`() {
        val summary = Person(name = "Ada", age = 36).asSummary()
        assertEquals(PersonSummary(name = "Ada"), summary)
    }

    @Test
    fun `mapper de interfaz delega en la extension`() {
        val dto = PersonMapperImpl.toDto(Person(name = "Ada", age = 36))
        assertEquals(PersonDto(name = "Ada", age = 36), dto)
    }

    @Test
    fun `var de cuerpo asignada post-construccion`() {
        val entity = TaskSrc(id = "T-1", status = "DONE").toTaskEntity()
        assertEquals("T-1", entity.id)
        assertEquals("DONE", entity.status)
    }

    @Test
    fun `WithDefault aplica la constante solo cuando la fuente es null`() {
        val defaulted = Profile(nickname = null, email = "a@b.c").toProfileDto()
        assertEquals("N/A", defaulted.nickname)
        val kept = Profile(nickname = "kuro", email = "a@b.c").toProfileDto()
        assertEquals("kuro", kept.nickname)
    }

    @Test
    fun `lista mapeada elemento a elemento con el mapper declarado`() {
        val dto = Directory(addresses = listOf(Address("Arequipa"), Address("Lima"))).toDirectoryDto()
        assertEquals(listOf(AddressDto("Arequipa"), AddressDto("Lima")), dto.addresses)
    }

    @Test
    fun `renombrado plano en runtime`() {
        assertEquals(UserDto(name = "Ada"), User(firstname = "Ada").toUserDto())
    }

    @Test
    fun `useTargetDefaults - null aplica el default y valor pasa`() {
        assertEquals("N/A", Handle(name = "Ada", nickname = null).toHandleDto().nickname)
        assertEquals("Lin", Handle(name = "Ada", nickname = "Lin").toHandleDto().nickname)
    }

    @Test
    fun `sealed dispatch - round por cada subtipo`() {
        val placed = OrderEvent.Placed(total = 100L).toOrderEventDto()
        assertEquals(OrderEventDto.Placed(total = 100L), placed)
        val cancelled: OrderEvent = OrderEvent.Cancelled
        assertEquals(OrderEventDto.Cancelled, cancelled.toOrderEventDto())
    }

    @Test
    fun `value class - wrap ejecuta el init validador y unwrap nullable compone`() {
        val ok = Contact(email = "ada@kmapx.dev", alias = null).toContactDto()
        assertEquals(Email("ada@kmapx.dev"), ok.email)
        assertEquals(null, ok.alias)
        assertEquals("lin", Contact(email = "a@b.c", alias = Alias("lin")).toContactDto().alias)
        assertFailsWith<IllegalArgumentException> {
            Contact(email = "sin-arroba", alias = null).toContactDto()
        }
    }

    @Test
    fun `patch inmutable - original intacto - null no toca - coleccion reemplaza`() {
        val original = Article(title = "Kmapx", tags = listOf("kotlin"), views = 7)
        val patched = ArticlePatcherImpl.apply(original, ArticlePatch(title = null, tags = listOf("ksp", "mapper")))
        // null = no tocar; colección = reemplazo completo; ausente en patch = se conserva:
        assertEquals("Kmapx", patched.title)
        assertEquals(listOf("ksp", "mapper"), patched.tags)
        assertEquals(7, patched.views)
        // El original JAMAS se muta:
        assertEquals(Article("Kmapx", listOf("kotlin"), 7), original)
        // Valor presente reemplaza:
        assertEquals("KmapX 1.0", ArticlePatcherImpl.apply(original, ArticlePatch(title = "KmapX 1.0", tags = null)).title)
    }

    @Test
    fun `estrategia aplicada en la post-asignacion de una var de cuerpo`() {
        assertEquals("OPEN", TicketSrc(id = "T-1", state = null).toTicket().state)
        assertEquals("CLOSED", TicketSrc(id = "T-1", state = "CLOSED").toTicket().state)
    }

    @Test
    fun `enum dispatch - round por cada entry`() {
        assertEquals(LevelDto.LOW, Level.LOW.toLevelDto())
        assertEquals(LevelDto.HIGH, Level.HIGH.toLevelDto())
    }

    @Test
    fun `anidado top-level via mapper declarado`() {
        val dto = Shipment(code = "S-1", destination = Address("Arequipa")).toShipmentDto()
        assertEquals(ShipmentDto("S-1", AddressDto("Arequipa")), dto)
    }

    @Test
    fun `Map Array y Result mapean en una pasada`() {
        val dto = Inventory(
            stock = mapOf("a" to Address("Arequipa")),
            checkpoints = arrayOf(Address("Lima")),
            lastSync = Result.success(Address("Cusco")),
        ).toInventoryDto()
        assertEquals(mapOf("a" to AddressDto("Arequipa")), dto.stock)
        assertEquals(AddressDto("Lima"), dto.checkpoints.single())
        assertEquals(AddressDto("Cusco"), dto.lastSync.getOrNull())
        // failure pasa intacto (semántica de Result.map):
        val boom = RuntimeException("boom")
        val failed = Inventory(emptyMap(), arrayOf(), Result.failure(boom)).toInventoryDto()
        assertEquals(boom, failed.lastSync.exceptionOrNull())
    }

    @Test
    fun `bidireccional - round-trip devuelve el original`() {
        val original = Vehicle(plate = PlateId("V1A-731"), year = 2021)
        val dto = original.toVehicleDto()
        assertEquals(VehicleDto(plateNumber = "V1A-731", year = 2021), dto)
        assertEquals(original, dto.toVehicle())
    }

    @Test
    fun `ruta anidada aplana con seguridad de null por segmento`() {
        assertEquals("Arequipa", BranchSrc("centro", Address("Arequipa")).toBranchDto().city)
        assertEquals(null, BranchSrc("centro", null).toBranchDto().city)
    }

    @Test
    fun `converter del usuario aplicado en runtime`() {
        val dto = Receipt(amount = 12345L).toReceiptDto()
        assertEquals("123.45", dto.amount)
    }

    @Test
    fun `converters calificados eligen formato por campo`() {
        val dto = Code(primary = 255, secondary = 255).toCodeDto()
        assertEquals("ff", dto.primary)   // Hex
        assertEquals("255", dto.secondary) // Dec
    }

    @Test
    fun `dominio limpio - converter y renombre viven en el metodo del Mapper`() {
        assertEquals(MoneyView("42"), MoneyMapperImpl.toView(Money(cents = 42)))
    }

    @Test
    fun `OrThrow lanza con mensaje claro cuando la fuente es null`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            Profile(nickname = "kuro", email = null).toProfileDto()
        }
        assertEquals("email must not be null mapping Profile -> ProfileDto", ex.message)
    }
}
