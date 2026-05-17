package cl.cadcc.ramitos.utils

import com.password4j.{BcryptFunction, Hash, HashBuilder, HashingFunction, Password}
import cl.cadcc.ramitos.config.BcryptConfig


trait Crypto {
    def hashPassword(password: String): String
    def verifyPassword(password: String, hash: String): Boolean
}

object Crypto {

    def ofConf(bcrypt: BcryptConfig): Crypto =
        CryptoImpl(bcrypt.rounds, bcrypt.pepper.getOrElse(""))
    
    private class CryptoImpl(rounds: Int, pepper: String) extends Crypto {

        private val hf: HashingFunction = BcryptFunction.getInstance(rounds)

        def hashPassword(password: String): String =
            val hash: Hash = Password.hash(password)
                .addPepper(pepper)
                .`with`(hf)
            
            hash.getResult()

        def verifyPassword(password: String, hash: String): Boolean =
            val hf: HashingFunction = BcryptFunction.getInstanceFromHash(hash)
            Password.check(password, hash).addPepper(pepper).`with`(hf)
    }
}
