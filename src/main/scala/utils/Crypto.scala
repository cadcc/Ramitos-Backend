package cl.cadcc.ramitos.utils


trait Crypto {
    def hashPassword(password: String): String
    def verifyPassword(secret: String): Boolean
}

object Crypto {
    
}
