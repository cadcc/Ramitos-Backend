package cl.cadcc.ramitos.utils

import com.password4j.Hash
import com.password4j.Password
import com.password4j.HashingFunction
import com.password4j.BcryptFunction


trait Crypto {
    def hashPassword(password: String): String
    def verifyPassword(password: String, hash: String): Boolean
}

object Crypto {

    def apply(using ev: Crypto) = ev

    given crypto: Crypto = CryptoImpl
    
    private object CryptoImpl extends Crypto {
        def hashPassword(password: String): String =
            val hash: Hash = Password.hash(password)
                .addRandomSalt(25)
                .withBcrypt()
            
            hash.getResult()

        def verifyPassword(password: String, hash: String): Boolean =
            val hf: HashingFunction = BcryptFunction.getInstanceFromHash(hash)
            Password.check(password, hash).`with`(hf)
    }
}
