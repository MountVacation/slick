package com.novocode.squery.test

import org.junit.Test
import org.junit.Assert._
import java.sql._
import scala.Array
import scala.collection.JavaConversions
import com.novocode.squery.ResultSetInvoker
import com.novocode.squery.simple._
import com.novocode.squery.simple.StaticQueryBase._
import com.novocode.squery.simple.Implicit._
import com.novocode.squery.session._
import com.novocode.squery.session.Database.threadLocalSession
import com.novocode.squery.test.util._
import com.novocode.squery.test.util.TestDB._

object SimpleTest extends DBTestObject(H2Mem, H2Disk, SQLiteMem, SQLiteDisk)

class SimpleTest(tdb: TestDB) extends DBTest(tdb) {

  implicit def rsToUser(rs: PositionedResult) = new User(rs.nextInt(), rs.nextString())

  case class User(id:Int, name:String)

  case class GetUsers(id: Option[Int]) extends DynamicQuery[User] {
    select ~ "id, name from users"
    id foreach { this ~ "where id =" ~? _ }
  }

  case class GetUsers2(id: Option[Int]) extends DynamicQuery[User] {
    select ~ "id, name from users"
    wrap("where id =", "") { id foreach(v => this ~? v) }
  }

  case class InsertUser(id: Int, name: String) extends DynamicQuery[Int] {
    this ~ "insert into USERS values (" ~? id ~ "," ~? name ~ ")"
  }

  @Test def test() {
    val createTable = updateNA("create table USERS(ID int not null primary key, NAME varchar(255))")
    val populateUsers = List(InsertUser(1, "szeiger"), InsertUser(0, "admin"), InsertUser(2, "guest"), InsertUser(3, "foo"))

    val allIDs = queryNA[Int]("select id from users")
    val userForID = query[Int,User]("select id, name from users where id = ?")

    db withSession {
      threadLocalSession.withTransaction {
        println("Creating user table: "+createTable.first)
        println("Inserting users:")
        for(i <- populateUsers) println("  "+i.first)
      }

      println("All IDs:")
      for(s <- allIDs.list) println("  "+s)
      assertEquals(Set(1,0,2,3), allIDs.list.toSet)

      println("All IDs with foreach:")
      var s1 = Set[Int]()
      allIDs foreach { s =>
        println("  "+s)
        s1 += s
      }
      assertEquals(Set(1,0,2,3), s1)

      val res = userForID.first(2)
      println("User for ID 2: "+res)
      assertEquals(res, User(2,"guest"))

      println("User 2 with foreach:")
      var s2 = Set[User]()
      userForID(2) foreach { s =>
        println("  "+s)
        s2 += s
      }
      assertEquals(Set(User(2,"guest")), s2)

      println("User 2 with foreach:")
      var s3 = Set[User]()
      GetUsers(Some(2)) foreach { s =>
        println("  "+s)
        s3 += s
      }
      assertEquals(Set(User(2,"guest")), s3)

      println("All users with foreach:")
      var s4 = Set[User]()
      GetUsers(None) foreach { s =>
        println("  "+s)
        s4 += s
      }
      assertEquals(Set(User(1,"szeiger"), User(2,"guest"), User(0,"admin"), User(3,"foo")), s4)

      println("All users with elements.foreach:")
      var s5 = Set[User]()
      for(s <- GetUsers(None).elements) {
        println("  "+s)
        s5 += s
      }
      assertEquals(Set(User(1,"szeiger"), User(2,"guest"), User(0,"admin"), User(3,"foo")), s5)

      println("All tables:")
      val tables = ResultSetInvoker[(String,String,String)](_.conn.getMetaData().getTables("", "", null, null))
      for(t <- tables) println("  "+t)
      assertEquals(List("USERS"),
        tables.list.map(_._3).filter(s => if(tdb.dbType == "sqlite") !s.toUpperCase.contains("SQLITE_") else true))
    } 
  }
}
