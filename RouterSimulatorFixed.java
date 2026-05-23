import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * IPv4 Yönlendirici Simülatörü - Longest Prefix Match (LPM) ile çıkış arayüzü seçimi.
 * 
 * Özellikler:
 * - Yönlendirme tablosu yönetimi (CIDR formatında)
 * - Longest Prefix Match (LPM) algoritması
 * - IP adresi normalizasyonu (host bitleri maskeleme)
 * - Bitwise AND işlemleri ile subnet karşılaştırma
 * 
 * Ağ Standartlarına Uygunluk:
 * - RFC 4632 (CIDR)
 * - RFC 1519 (Classless Inter-Domain Routing)
 * - Unsigned int bit manipülasyonu (IPv4 32-bit adresler)
 */
public class RouterSimulatorFixed {

    private final List<RouteEntry> routes = new ArrayList<>();

    /**
     * Yönlendirme tablosuna bir önek ekler.
     *
     * @param networkCidr ağ adresi CIDR gösterimi (örn. {@code 192.168.0.0/16});
     *                      ana bilgisayar bitleri maskelenerek ağ adresine normalize edilir
     * @param interfaceId   paketin eşleşmede yönlendirileceği arayüz tanımlayıcısı (örn. {@code eth0})
     * @throws IllegalArgumentException CIDR veya arayüz geçersizse
     */
    public void addRoute(String networkCidr, String interfaceId) {
        routes.add(RouteEntry.fromCidr(networkCidr, interfaceId));
    }

    /**
     * Tablodaki rotaların kopyasını döndürür (değiştirilemez liste).
     */
    public List<RouteEntry> getRoutes() {
        return Collections.unmodifiableList(new ArrayList<>(routes));
    }

    /**
     * Hedef IPv4 adresi için LPM uygular; en uzun önekle eşleşen arayüzü döndürür.
     *
     * <p>Longest Prefix Match Algoritması:
     * <ol>
     *   <li>Hedef IP'yi 32-bit unsigned int olarak parse et</li>
     *   <li>Her rota için: (dest & mask) == network kontrolü</li>
     *   <li>En uzun önekli (en büyük prefixLength) eşleşmeyi seç</li>
     * </ol>
     *
     * @param destinationIpv4 hedef adres (örn. {@code 192.168.1.10})
     * @return eşleşme varsa arayüz ve eşleşen rota bilgisi
     */
    public Optional<ForwardingDecision> forward(String destinationIpv4) {
        int dest = Ipv4Parsing.parseToUnsignedInt(destinationIpv4);
        RouteEntry best = null;
        
        for (RouteEntry entry : routes) {
            if (entry.matches(dest)) {
                // Longest Prefix Match: Daha uzun önek kazanır
                if (best == null || entry.getPrefixLength() > best.getPrefixLength()) {
                    best = entry;
                }
            }
        }
        
        if (best == null) {
            return Optional.empty();
        }
        return Optional.of(new ForwardingDecision(best.getInterfaceId(), best));
    }

    /**
     * LPM sonucu: seçilen çıkış arayüzü ve eşleşen tablo girdisi.
     */
    public static final class ForwardingDecision {
        private final String interfaceId;
        private final RouteEntry matchedRoute;

        public ForwardingDecision(String interfaceId, RouteEntry matchedRoute) {
            this.interfaceId = Objects.requireNonNull(interfaceId, "interfaceId");
            this.matchedRoute = Objects.requireNonNull(matchedRoute, "matchedRoute");
        }

        public String getInterfaceId() {
            return interfaceId;
        }

        public RouteEntry getMatchedRoute() {
            return matchedRoute;
        }
    }

    /**
     * Yönlendirme tablosu satırı: ağ öneki + çıkış arayüzü.
     * 
     * <p>Subnet Eşleşme Mantığı:
     * {@code (destinationAddress & subnetMask) == networkAddress}
     */
    public static final class RouteEntry {
        private final int networkAddress;   // Ağ adresi (host bitleri 0)
        private final int prefixLength;     // /24, /16, /32 vb.
        private final int subnetMask;       // Önek uzunluğuna göre hesaplanan maske
        private final String interfaceId;

        private RouteEntry(int networkAddress, int prefixLength, int subnetMask, String interfaceId) {
            this.networkAddress = networkAddress;
            this.prefixLength = prefixLength;
            this.subnetMask = subnetMask;
            this.interfaceId = interfaceId;
        }

        /**
         * CIDR string'den RouteEntry oluşturur.
         * 
         * <p>Normalizasyon:
         * <ul>
         *   <li>Host bitleri maske ile temizlenir: {@code raw & mask}</li>
         *   <li>Önek uzunluğu 0-32 aralığında olmalıdır</li>
         * </ul>
         *
         * @param cidr CIDR gösterimi (örn. "192.168.1.0/24")
         * @param interfaceId çıkış arayüzü
         * @return RouteEntry nesnesi
         * @throws IllegalArgumentException geçersiz CIDR veya arayüz ise
         */
        public static RouteEntry fromCidr(String cidr, String interfaceId) {
            if (interfaceId == null || interfaceId.isBlank()) {
                throw new IllegalArgumentException("interfaceId boş olamaz.");
            }
            
            String trimmed = Objects.requireNonNull(cidr, "cidr").trim();
            int slash = trimmed.indexOf('/');
            if (slash < 0) {
                throw new IllegalArgumentException("CIDR '/' ile önek uzunluğu içermelidir: " + cidr);
            }
            
            String addrPart = trimmed.substring(0, slash).trim();
            String lenPart = trimmed.substring(slash + 1).trim();
            
            int prefixLength;
            try {
                prefixLength = Integer.parseInt(lenPart);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Geçersiz önek uzunluğu: " + lenPart, e);
            }
            
            if (prefixLength < 0 || prefixLength > 32) {
                throw new IllegalArgumentException("Önek uzunluğu 0–32 arasında olmalıdır: " + prefixLength);
            }
            
            int mask = Ipv4Parsing.prefixLengthToMask(prefixLength);
            int raw = Ipv4Parsing.parseToUnsignedInt(addrPart);
            // Kritik: Host bitleri maske ile temizleniyor (normalize)
            int network = raw & mask;
            
            return new RouteEntry(network, prefixLength, mask, interfaceId.trim());
        }

        /**
         * Hedef adres bu route'un subnetine dahil mi?
         * 
         * <p>Matematiksel olarak: {@code (dest & mask) == network}
         *
         * @param destinationAddress 32-bit unsigned int hedef IP
         * @return true ise eşleşme var
         */
        boolean matches(int destinationAddress) {
            return (destinationAddress & subnetMask) == networkAddress;
        }

        public int getNetworkAddress() {
            return networkAddress;
        }

        public int getPrefixLength() {
            return prefixLength;
        }

        public int getSubnetMask() {
            return subnetMask;
        }

        public String getInterfaceId() {
            return interfaceId;
        }

        /** CIDR gösterimindeki ağ adresi (normalize). */
        public String getNetworkCidrString() {
            return Ipv4Parsing.unsignedIntToDottedString(networkAddress) + "/" + prefixLength;
        }
    }

    /** 
     * IPv4 yardımcı metodları - Bit manipülasyonu.
     * 
     * <p>Java int signed olduğu için unsigned işlemler için dikkatli bit manipülasyonu gerekir.
     */
    static final class Ipv4Parsing {

        private Ipv4Parsing() {
        }

        /**
         * IPv4 adresini 32-bit unsigned int'e dönüştürür.
         * 
         * <p>Byte sırası: big-endian (network order)
         * {@code (b0 << 24) | (b1 << 16) | (b2 << 8) | b3}
         *
         * @param dottedDecimal IPv4 adresi (örn. "192.168.1.1")
         * @return 32-bit unsigned int (Java int olarak saklanır, signed interpretasyonu ignore edilir)
         * @throws IllegalArgumentException geçersiz IPv4 veya IPv6 ise
         */
        static int parseToUnsignedInt(String dottedDecimal) {
            try {
                InetAddress inet = InetAddress.getByName(dottedDecimal.trim());
                byte[] bytes = inet.getAddress();
                if (bytes.length != 4) {
                    throw new IllegalArgumentException("Yalnızca IPv4 desteklenir: " + dottedDecimal);
                }
                // Big-endian byte order (network byte order)
                return ((bytes[0] & 0xFF) << 24)
                        | ((bytes[1] & 0xFF) << 16)
                        | ((bytes[2] & 0xFF) << 8)
                        | (bytes[3] & 0xFF);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Geçersiz IPv4 adresi: " + dottedDecimal, e);
            }
        }

        /**
         * Önek uzunluğundan subnet maskesi hesaplar.
         * 
         * <p>Örnek:
         * <ul>
         *   <li>/0  → 0x00000000</li>
         *   <li>/1  → 0x80000000 (10000000...)</li>
         *   <li>/8  → 0xFF000000</li>
         *   <li>/16 → 0xFFFF0000</li>
         *   <li>/24 → 0xFFFFFF00</li>
         *   <li>/32 → 0xFFFFFFFF</li>
         * </ul>
         *
         * @param prefixLength 0-32 arası önek uzunluğu
         * @return 32-bit subnet maskesi
         */
        static int prefixLengthToMask(int prefixLength) {
            if (prefixLength <= 0) {
                return 0;  // /0: tüm bitler host biti
            }
            if (prefixLength >= 32) {
                return 0xFFFFFFFF;  // /32: tüm bitler network biti
            }
            // Önemli: 0xFFFFFFFFL (long) kullanarak signed int overflow'dan kaçınılır
            // Sonra int'e cast edilir (bitsel aynı kalır)
            return (int) (0xFFFFFFFFL << (32 - prefixLength));
        }

        /**
         * 32-bit unsigned int'i noktalı ondalık formata dönüştürür.
         * 
         * <p>Unsigned shift (>>>) kullanarak sign extension'dan kaçınılır.
         *
         * @param value 32-bit IP adresi
         * @return noktalı ondalık string (örn. "192.168.1.1")
         */
        static String unsignedIntToDottedString(int value) {
            return ((value >>> 24) & 0xFF) + "."
                    + ((value >>> 16) & 0xFF) + "."
                    + ((value >>> 8) & 0xFF) + "."
                    + (value & 0xFF);
        }
    }
}
