/*
 * Copyright 2014-2016 Panavista Technologies, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.deftrade.db

import slick.backend.DatabasePublisher
import java.time.OffsetDateTime
import scala.language.postfixOps
import scala.concurrent.{ Future, ExecutionContext }

import scala.concurrent.{ Await, duration }
import duration._
import org.scalatest._

import io.deftrade.test.Tables._

import com.github.tminglei.slickpg.{ JsonString, Range => PgRange }
object Misc {
  def now = OffsetDateTime.now()
  val yearStart = OffsetDateTime.parse("2016-01-01T00:00:00+00:00")
  val ytd: Span = PgRange(yearStart, yearStart).copy(end = None)  // WAT
  def fromNow: Span = PgRange(now, now).copy(end = None)
}
trait PgSpec extends FlatSpec with Matchers with BeforeAndAfterEach {

  import profile.api._
  lazy val db = Database.forConfig("postgres")

  implicit val globalTimeout = 2 seconds

  def exec[T](action: DBIO[T])(implicit ex: ExecutionContext, timeout: Duration): T = {
    Await.result(db.run(action), timeout)
  }
  def execTransactionally[T](action: DBIO[T])(implicit ex: ExecutionContext, timeout: Duration): T = {
    Await.result(db.run(action.transactionally), timeout)
  }

  /**
    * Execute the `action` transactionally and return the value, but roll back the transaction.
    * - if `action` succeeds, return the result and rollback
    * - if `action` fails, throw the exception
    */
  def execAndRollback[T](action: DBIO[T])(implicit ex: ExecutionContext, timeout: Duration): T = {

    case class ResultEscapePod(t: T) extends Throwable

    val actionWhichWillFailAndRollBack = for {
      t <- action
      _ <- DBIO failed ResultEscapePod(t)
    } yield t

    val future = db run actionWhichWillFailAndRollBack.transactionally recoverWith {
      case ResultEscapePod(t) => Future successful t
      case e                  => Future failed e
    }
    Await.result(future, timeout)
  }

  def stream[T](action: StreamingDBIO[T, T]): DatabasePublisher[T] = ???

}

class RepoSpec extends PgSpec {
  import Misc._
  import ExecutionContext.Implicits.global

  val user0 = User(span = ytd, userName = "Binky", signup = yearStart, btcAddr = "1337", meta = JsonString("{}"))
  
  behavior of "the base repo"

  it should "insert a record" in {
    val nrows = exec {
//      Users.insert(user0)
      user0.insert()
    }
    nrows.userName should be (user0.userName)
  }
  
  it should "delete a record" in {
    val nrows = exec {
      Users.delete()
    }
    nrows should be (1)
  }
}