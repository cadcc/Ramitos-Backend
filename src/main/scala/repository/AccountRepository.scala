package cl.cadcc.ramitos.repository

import cats._, cats.data._, cats.implicits._
import doobie._, doobie.implicits._
import cl.cadcc.ramitos.model.Account
import cl.cadcc.ramitos.model.AccountRole
import scala.collection.mutable.ListBuffer

object AccountRepository {

    def create(displayName: String, role: AccountRole): ConnectionIO[Account] =
        sql"INSERT INTO accounts (name, role) values ($displayName, $role)"
        .update
        .withUniqueGeneratedKeys("id", "name", "role", "created_at", "updated_at")

    def update(id: Int, displayName: Option[String] = None, role: Option[AccountRole]): ConnectionIO[Account] =
        var fragList: ListBuffer[Fragment] = ListBuffer()

        displayName match {
            case Some(value) => fragList.addAll(Seq(fr", ", fr"name = $value"))
            case _ => 
        }
        role match {
            case Some(value) => fragList.addAll(Seq(fr", ", fr"role = $value"))
            case _ => 
        }

        if(fragList.nonEmpty){
            fragList.addOne(fr" WHERE id is $id")
            var query = fragList.tail.fold(fr"UPDATE account SET ")
                ((a,b) => a++b)

            query.update
            .withUniqueGeneratedKeys("id", "name", "role", "created_at", "updated_at")
        } else {
            throw Exception("Account Repository: No field to update")
        }

    def getById(id: Int): ConnectionIO[Option[Account]] =
        sql"SELECT * FROM accounts WHERE id = {id}"
            .query[Account]
            .option
}
