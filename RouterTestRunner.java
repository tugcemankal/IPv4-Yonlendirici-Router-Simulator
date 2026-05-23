import java.lang.reflect.Method;
import java.util.Optional;

/**
 * RouterSimulator manuel test runner
 * JUnit olmadan temel fonksiyon testleri
 */
public class RouterTestRunner {
    
    private static int passed = 0;
    private static int failed = 0;
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== RouterSimulator Manuel Testler ===\n");
        
        testBasicLPM();
        testNetworkNormalization();
        testSlash32Prefix();
        testDefaultRoute();
        testOverlappingRoutes();
        testInvalidCidr();
        testInvalidInterfaceId();
        testMaskCalculation();
        testIpParsing();
        testIpToString();
        testSmallSubnets();
        testBitwiseMasking();
        testIpv6Rejected();
        
        System.out.println("\n=== Sonuç ===");
        System.out.println("Başarılı: " + passed);
        System.out.println("Başarısız: " + failed);
        System.out.println("Toplam: " + (passed + failed));
        
        if (failed > 0) {
            System.exit(1);
        }
    }
    
    private static void testBasicLPM() {
        test("Basic LPM - En uzun önek kazanır", () -> {
            RouterSimulator router = new RouterSimulator();
            router.addRoute("0.0.0.0/0", "eth0");
            router.addRoute("192.168.0.0/16", "eth1");
            router.addRoute("192.168.1.0/24", "eth2");
            
            Optional<RouterSimulator.ForwardingDecision> result = router.forward("192.168.1.50");
            assertTrue(result.isPresent(), "Eşleşme bulunamadı");
            assertEquals("eth2", result.get().getInterfaceId(), "En uzun önek /24 olmalı");
        });
    }
    
    private static void testNetworkNormalization() {
        test("Network Normalization - Host bitleri maskelenir", () -> {
            RouterSimulator router = new RouterSimulator();
            // 192.168.1.100/24 ekliyoruz, ama ağ adresi 192.168.1.0 olmalı
            router.addRoute("192.168.1.100/24", "eth0");
            
            Optional<RouterSimulator.ForwardingDecision> result = router.forward("192.168.1.50");
            assertTrue(result.isPresent(), "Eşleşme bulunamadı");
            
            String cidr = result.get().getMatchedRoute().getNetworkCidrString();
            assertEquals("192.168.1.0/24", cidr, "Ağ adresi normalize edilmeli");
        });
    }
    
    private static void testSlash32Prefix() {
        test("/32 önek - Tam adres eşleşmesi", () -> {
            RouterSimulator router = new RouterSimulator();
            router.addRoute("192.168.1.1/32", "lo0");
            router.addRoute("192.168.1.0/24", "eth0");
            
            // Sadece 192.168.1.1 ile eşleşmeli
            Optional<RouterSimulator.ForwardingDecision> result = router.forward("192.168.1.1");
            assertTrue(result.isPresent(), "192.168.1.1 eşleşmeli");
            assertEquals("lo0", result.get().getInterfaceId(), "/32 ile eşleşmeli");
            
            // 192.168.1.2 /32 ile eşleşmez, /24 ile eşleşir
            result = router.forward("192.168.1.2");
            assertTrue(result.isPresent(), "192.168.1.2 eşleşmeli");
            assertEquals("eth0", result.get().getInterfaceId(), "/24 ile eşleşmeli");
        });
    }
    
    private static void testDefaultRoute() {
        test("/0 önek - Varsayılan rota", () -> {
            RouterSimulator router = new RouterSimulator();
            router.addRoute("0.0.0.0/0", "eth0");
            router.addRoute("192.168.0.0/16", "eth1");
            
            Optional<RouterSimulator.ForwardingDecision> result = router.forward("10.0.0.1");
            assertTrue(result.isPresent(), "Eşleşme bulunamadı");
            assertEquals("eth0", result.get().getInterfaceId(), "Default rota kullanılmalı");
        });
    }
    
    private static void testOverlappingRoutes() {
        test("Çakışan route'lar - LPM doğru seçim", () -> {
            RouterSimulator router = new RouterSimulator();
            router.addRoute("0.0.0.0/0", "eth0");
            router.addRoute("192.0.0.0/8", "eth1");
            router.addRoute("192.168.0.0/16", "eth2");
            router.addRoute("192.168.1.0/24", "eth3");
            router.addRoute("192.168.1.1/32", "lo0");
            
            // Test caseleri
            assertRoute(router, "192.168.1.1", "lo0", "En spesifik: /32");
            assertRoute(router, "192.168.1.2", "eth3", "/24");
            assertRoute(router, "192.168.2.1", "eth2", "/16");
            assertRoute(router, "192.10.1.1", "eth1", "/8");
            assertRoute(router, "10.0.0.1", "eth0", "Default");
        });
    }
    
    private static void testInvalidCidr() {
        test("Geçersiz CIDR formatları", () -> {
            RouterSimulator router = new RouterSimulator();
            
            // / eksik
            assertThrows(() -> router.addRoute("192.168.1.0", "eth0"), 
                "/ eksik CIDR hata vermeli");
            
            // /33 (geçersiz)
            assertThrows(() -> router.addRoute("192.168.1.0/33", "eth0"), 
                "/33 hata vermeli");
            
            // Negatif önek
            assertThrows(() -> router.addRoute("192.168.1.0/-1", "eth0"), 
                "Negatif önek hata vermeli");
        });
    }
    
    private static void testInvalidInterfaceId() {
        test("Geçersiz arayüz ID", () -> {
            RouterSimulator router = new RouterSimulator();
            
            assertThrows(() -> router.addRoute("192.168.1.0/24", null), 
                "null interfaceId hata vermeli");
            assertThrows(() -> router.addRoute("192.168.1.0/24", ""), 
                "Boş interfaceId hata vermeli");
            assertThrows(() -> router.addRoute("192.168.1.0/24", "   "), 
                "Whitespace interfaceId hata vermeli");
        });
    }
    
    private static void testMaskCalculation() throws Exception {
        test("Mask hesaplama", () -> {
            Method method = RouterSimulator.class.getDeclaredMethod("prefixLengthToMask", int.class);
            method.setAccessible(true);
            
            // /0 -> 0x00000000
            assertEquals(0, (int) method.invoke(null, 0), "/0 mask = 0");
            
            // /1 -> 0x80000000
            assertEquals(0x80000000, (int) method.invoke(null, 1), "/1 mask");
            
            // /8 -> 0xFF000000
            assertEquals(0xFF000000, (int) method.invoke(null, 8), "/8 mask");
            
            // /16 -> 0xFFFF0000
            assertEquals(0xFFFF0000, (int) method.invoke(null, 16), "/16 mask");
            
            // /24 -> 0xFFFFFF00
            assertEquals(0xFFFFFF00, (int) method.invoke(null, 24), "/24 mask");
            
            // /32 -> 0xFFFFFFFF
            assertEquals(0xFFFFFFFF, (int) method.invoke(null, 32), "/32 mask");
        });
    }
    
    private static void testIpParsing() throws Exception {
        test("IP parse dönüşümü", () -> {
            Method method = RouterSimulator.class.getDeclaredMethod("parseToUnsignedInt", String.class);
            method.setAccessible(true);
            
            // 0.0.0.0 -> 0
            assertEquals(0, (int) method.invoke(null, "0.0.0.0"), "0.0.0.0 parse");
            
            // 192.168.1.1 -> 0xC0A80101
            int expected = (192 << 24) | (168 << 16) | (1 << 8) | 1;
            assertEquals(expected, (int) method.invoke(null, "192.168.1.1"), "192.168.1.1 parse");
            
            // 255.255.255.255 -> 0xFFFFFFFF
            assertEquals(0xFFFFFFFF, (int) method.invoke(null, "255.255.255.255"), "Max IP parse");
        });
    }
    
    private static void testIpToString() throws Exception {
        test("IP to string dönüşümü", () -> {
            Method method = RouterSimulator.class.getDeclaredMethod("unsignedIntToDottedString", int.class);
            method.setAccessible(true);
            
            assertEquals("0.0.0.0", method.invoke(null, 0), "0 to string");
            assertEquals("192.168.1.1", method.invoke(null, (192 << 24) | (168 << 16) | (1 << 8) | 1), 
                "192.168.1.1 to string");
            assertEquals("255.255.255.255", method.invoke(null, 0xFFFFFFFF), "Max IP to string");
        });
    }
    
    private static void testSmallSubnets() {
        test("/30 küçük subnet", () -> {
            RouterSimulator router = new RouterSimulator();
            router.addRoute("192.168.1.0/30", "eth0");
            
            // /30 aralığı: .0, .1, .2, .3
            String[] inRange = {"192.168.1.0", "192.168.1.1", "192.168.1.2", "192.168.1.3"};
            String[] outOfRange = {"192.168.1.4", "192.168.0.255", "192.168.2.0"};
            
            for (String ip : inRange) {
                Optional<RouterSimulator.ForwardingDecision> result = router.forward(ip);
                assertTrue(result.isPresent(), ip + " /30 aralığında olmalı");
            }
            
            for (String ip : outOfRange) {
                Optional<RouterSimulator.ForwardingDecision> result = router.forward(ip);
                assertFalse(result.isPresent(), ip + " /30 aralığında olmamalı");
            }
        });
    }
    
    private static void testBitwiseMasking() {
        test("Bitwise AND masking - sınır değerler", () -> {
            RouterSimulator router = new RouterSimulator();
            router.addRoute("128.0.0.0/1", "eth0");
            
            // 128.0.0.0/1: 128-255 arası (MSB = 1)
            Optional<RouterSimulator.ForwardingDecision> result = router.forward("128.0.0.1");
            assertTrue(result.isPresent(), "128.0.0.1 /1 ile eşleşmeli");
            assertEquals("eth0", result.get().getInterfaceId(), "Bitwise /1 interface");
            
            result = router.forward("255.255.255.255");
            assertTrue(result.isPresent(), "255.255.255.255 /1 ile eşleşmeli");
            
            // 127.0.0.1 MSB = 0, eşleşmemeli
            result = router.forward("127.0.0.1");
            assertFalse(result.isPresent(), "127.0.0.1 /1 ile eşleşmemeli");
        });
    }
    
    private static void testIpv6Rejected() {
        test("IPv6 reddedilmeli", () -> {
            RouterSimulator router = new RouterSimulator();
            
            assertThrows(() -> router.addRoute("2001:db8::/32", "eth0"), 
                "IPv6 CIDR hata vermeli");
            assertThrows(() -> router.forward("2001:db8::1"), 
                "IPv6 forward hata vermeli");
        });
    }
    
    // ============ Test yardımcıları ============
    
    private static void test(String name, TestRunnable runnable) {
        try {
            runnable.run();
            System.out.println("✅ PASS: " + name);
            passed++;
        } catch (Exception e) {
            System.out.println("❌ FAIL: " + name);
            System.out.println("   " + e.getMessage());
            failed++;
        }
    }
    
    private static void assertRoute(RouterSimulator router, String dest, String expectedInterface, String msg) throws Exception {
        Optional<RouterSimulator.ForwardingDecision> result = router.forward(dest);
        if (!result.isPresent()) {
            throw new AssertionError(dest + " eşleşme bulunamadı: " + msg);
        }
        if (!expectedInterface.equals(result.get().getInterfaceId())) {
            throw new AssertionError(dest + " için beklenen: " + expectedInterface + 
                ", bulunan: " + result.get().getInterfaceId() + " (" + msg + ")");
        }
    }
    
    private static void assertTrue(boolean condition, String msg) {
        if (!condition) throw new AssertionError(msg);
    }
    
    private static void assertFalse(boolean condition, String msg) {
        if (condition) throw new AssertionError(msg);
    }
    
    private static void assertEquals(Object expected, Object actual, String msg) {
        if (!expected.equals(actual)) {
            throw new AssertionError(msg + " - Beklenen: " + expected + ", Gerçek: " + actual);
        }
    }
    
    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Beklenen: " + expected + ", Gerçek: " + actual);
        }
    }
    
    private static void assertThrows(Runnable runnable, String msg) {
        try {
            runnable.run();
            throw new AssertionError(msg + " - Exception atılmadı");
        } catch (IllegalArgumentException | NullPointerException e) {
            // Beklenen exception
        }
    }
    
    @FunctionalInterface
    interface TestRunnable {
        void run() throws Exception;
    }
    
    @FunctionalInterface
    interface Runnable {
        void run();
    }
}
