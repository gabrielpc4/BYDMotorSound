import Foundation
import Security

enum KeychainStore {
    private static let service = "com.gabrielpc.EngineLoudnessMeter"
    private static let tokenAccount = "byd-pairing-token"

    static func loadToken() -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: tokenAccount,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess else { return nil }
        return item as? Data
    }

    static func saveToken(_ token: Data) {
        let identity: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: tokenAccount,
        ]
        let attributes: [String: Any] = [kSecValueData as String: token]
        if SecItemUpdate(identity as CFDictionary, attributes as CFDictionary) == errSecItemNotFound {
            var item = identity
            item[kSecValueData as String] = token
            SecItemAdd(item as CFDictionary, nil)
        }
    }
}
