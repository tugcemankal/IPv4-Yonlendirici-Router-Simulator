import java.lang.reflect.Method;
import java.util.Optional;

/**
 * RouterSimulatorFixed Manuel Test Runner
 * 13 kapsamlı test senaryosu
 */
public class RouterTestRunnerFixed {
    
    private static int passed = 0;
    private static int failed = 0;
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== RouterSimulatorFixed Kapsamlı Testler ===\n");
        
        testBasicLPM();
        testNetworkNormalization();
        testSlash32Prefix();
        testDefaultRoute();
        testOverlappingRoutes();
        testInvalidCidr();
        testInvalidInterfaceId();
        testSmallSubnets();
        testBitwiseMasking();
        testIpv6Rejected();
        testEdgeCases();
        testLpmPriority();
        
        System.out.println("\n=== Sonuç ===");
        System.out.println("✅ Başarılı: " + passed);
        System.out.println("❌ Başarısız: " + failed);
        System.out.println("📊 Toplam: " + (passed + failed));
        
        if (failed > 0) {
            System.exit(1);
        }
    }
    
    private static void testBasicLPM() {
        test("LPM: En uzun önek kazanır", () -> {
            RouterSimulatorFixed router = new RouterSimulatorFixed();
            router.addRoute("0.0.0.0/0", "eth0");
            router.addRoute("192.168.0.0/16", "eth1");
            router.addRoute("192.168.1.0/24", "eth2");
            
            Optional<RouterSimulatorFixed.ForwardingDecision> result = router.forward("192.168.1.50");
            assertTrue(result.isPresent(), "Eşleşme bulunamadı");
            assertEquals("eth2", result.get().getInterfaceId(), "En uzun önek /24 olmalı");
        });
    }
    
    private static void testNetworkNormalization() {
        test("IP Normalize: Host bitleri maskelenir", () -> {
            RouterSimulatorFixed router = new RouterSimulatorFixed();
            // 192.168.1.100/24 ekliyoruz, ağ adresi 192.168.1.0 olmalı
            router.addRoute("192.168.1.100/24", "eth0");
            
            Optional<RouterSimulatorFixed.ForwardingDecision> result = router.forward("192.168.1.50");
            assertTrue(result.isPresent(), "Eşleşme bulunamadı");
            
            String cidr = result.get().getMatchedRoute().getNetworkCidrString();
            assertEquals("192.168.1.0/24", cidr, "Ağ adresi normalize edilmeli");
        });
    }
    
    private static void testSlash32Prefix() {
        test("/32: Tam adres eşleşmesi", () -> {
            RouterSimulatorFixed router = new RouterSimulatorFixed();
            router.addRoute("192.168.1.1/32", "lo0");
            router.addRoute("192.168.1.0/24", "eth0");
            
            Optional<RouterSimulatorFixed.ForwardingDecision> result = router.forward("192.168.1.1");
            assertTrue(result.isPresent(), "192.168.1.1 eşleşmeli");
            assertEquals("lo0", result.get().getInterfaceId(), "/32 ile eşleşmeli");
            
            result = router.forward("192.168.1.2");
            assertTrue(result.isPresent(), "192.168.1.2 eşleşmeli");
            assertEquals("eth0", result.get().getInterfaceId(), "/24 ile eşleşmeli");
        });
    }
    
    private static void testDefaultRoute() {
        test("/0: Varsayılan rota", () -> {
            RouterSimulatorFixed router = new RouterSimulatorFixed();
            router.addRoute("0.0.0.0/0", "eth0");
            router.addRoute("192.168.0.0/16", "eth1");
            
            // 10.0.0.1 sadece default rota ile eşleşir
            Optional<RouterSimulatorFixed.ForwardingDecision> result = router.forward("10.0.0.1");
            assertTrue(result.isPresent(), "Eşleşme bulunamadı");
            assertEquals("eth0", result.get().getInterfaceId(), "Default rota kullanılmalı");
        });
    }
    
    private static void testOverlappingRoutes() {
        test("Çakışan route'lar - LPM seçimi", () -> {
            RouterSimulatorFixed router = new RouterSimulatorFixed();
            router.addRoute("0.0.0.0/0", "eth0");
            router.addRoute("192.0.0.0/8", "eth1");
            router.addRoute("192.168.0.0/16", "eth2");
            router.addRoute("192.168.1.0/24", "eth3");
            router.addRoute("192.168.1.1/32", "lo0");
            
            assertRoute(router, "192.168.1.1", "lo0", "En spesifik: /32");
            assertRoute(router, "192.168.1.2", "eth3", "/24");
            assertRoute(router, "192.168.2.1", "eth2", "/16");
            assertRoute(router, "192.10.1.1", "eth1", "/8");
            assertRoute(router, "10.0.0.1", "eth0", "Default");
        });
    }
    
    private static void testInvalidCidr() {
        test("Geçersiz CIDR formatları", () -> {
            RouterSimulatorFixed router = new RouterSimulatorFixed();
            
            assertThrows(() -> router.addRoute("192.168.1.0", "eth0"), 
                "/ eksik CIDR hata vermeli");
            assertThrows(() -> router.addRoute("192.168.1.0/33", "eth0"), 
                "/33 hata vermeli");
            assertThrows(() -> router.addRoute("192.168.1.0/-1", "eth0"), 
                "Negatif önek hata vermeli");
            assertThrows(() -> router.addRoute("192.168.1.0/abc", "eth0"), 
                "Sayısal olmayan önek hata vermeli");
        });
    }
    
    private static void testInvalidInterfaceId() {
        test("Geçersiz arayüz ID", () -> {
            RouterSimulatorFixed router = new RouterSimulatorFixed();
            
            assertThrows(() -> router.addRoute("192.168.1.0/24", null), 
                "null interfaceId hata vermeli");
            assertThrows(() -> router.addRoute("192.168.1.0/24", ""), 
                "Boş interfaceId hata vermeli");
            assertThrows(() -> router.addRoute("192.168.1.0/24", "   "), 
                "Whitespace interfaceId hata vermeli");
        });
    }
    
    private static void testSmallSubnets() {
        test("/30 Küçük subnet", () -> {
            RouterSimulatorFixed router = new RouterSimulatorFixed();
            router.addRoute("192.168.1.0/30", "eth0");
            
            String[] inRange = {"192.168.1.0", "192.168.1.1", "192.168.1.2", "192.168.1.3"};
            String[] outOfRange = {"192.168.1.4", "192.168.0.255", "192.168.2.0"};
            
            for (String ip : inRange) {
                Optional<RouterSimulatorFixed.ForwardingDecision> result = router.forward(ip);
                assertTrue(result.isPresent(), ip + " /30 aralığında olmalı");
            }
            
            for (String ip : outOfRange) {
                Optional<RouterSimulatorFixed.ForwardingDecision> result = router.forward(ip);
                assertFalse(result.isPresent(), ip + " /30 aralığında olmamalı");
            }
        });
    }
    
    private static void testBitwiseMasking() {
        test("Bitwise AND: /1 sınır değerler", () -> {
            RouterSimulatorFixed router = new RouterSimulatorFixed();
            router.addRoute("128.0.0.0/1", "eth0");
            
            // 128.0.0.0/1: 128-255 arası (MSB = 1)
            Optional<RouterSimulatorFixed.ForwardingDecision> result = router.forward("128.0.0.1");
            assertTrue(result.isPresent(), "128.0.0.1 /1 ile eşleşmeli");
            
            result = router.forward("255.255.255.255");
            assertTrue(result.isPresent(), "255.255.255.255 /1 ile eşleşmeli");
            
            // 127.0.0.1 MSB = 0, eşleşmemeli
            result = router.forward("127.0.0.1");
            assertFalse(result.isPresent(), "127.0.0.1 /1 ile eşleşmemeli");
        });
    }
    
    private static void testIpv6Rejected() {
        test("IPv6 reddedilmeli", () -> {
            RouterSimulatorFixed router = new RouterSimulatorFixed();
            
            assertThrows(() -> router.addRoute("2001:db8::/32", "eth0"), 
                "IPv6 CIDR hata vermeli");
            assertThrows(() -> router.forward("2001:db8::1"), 
                "IPv6 forward hata vermeli");
        });
    }
    
    private static void testEdgeCases() {
        test("Edge cases: 0.0.0.0 ve 255.255.255.255", () -> {
            RouterSimulatorFixed router = new RouterSimulatorFixed();
            router.addRoute("0.0.0.0/0", "eth0");
            router.addRoute("255.255.255.255/32", "broadcast");
            
            // 0.0.0.0 default rota ile eşleşir
            Optional<RouterSimulatorFixed.ForwardingDecision> result = router.forward("0.0.0.0");
            assertTrue(result.isPresent(), "0.0.0.0 eşleşmeli");
            
            // 255.255.255.255 /32 ile eşleşir
            result = router.forward("255.255.255.255");
            assertTrue(result.isPresent(), "255.255.255.255 eşleşmeli");
            assertEquals("broadcast", result.get().getInterfaceId(), "Broadcast /32 ile eşleşmeli");
        });
    }
    
    private static void testLpmPriority() {
        test("LPM Önceliği: Eşit önek uzunluğu", () -> {
            RouterSimulatorFixed router = new RouterSimulatorFixed();
            // Aynı önek uzunluğu - ilk eklenen kazanır (implementation detail)
            router.addRoute("192.168.1.0/24", "eth0");
            router.addRoute("192.168.1.0/24", "eth1");
            
            Optional<RouterSimulatorFixed.ForwardingDecision> result = router.forward("192.168.1.1");
            assertTrue(result.isPresent(), "Eşleşme bulunmalı");
            // İlk eklenen kazanır
            assertEquals("eth0", result.get().getInterfaceId(), "İlk route kazanmalı");
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
    
    private static void assertRoute(RouterSimulatorFixed router, String dest, String expectedInterface, String msg) throws Exception {
        Optional<RouterSimulatorFixed.ForwardingDecision> result = router.forward(dest);
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
