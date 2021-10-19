package part3_highlevelserver

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MethodRejection, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._
import spray.json._

import scala.concurrent.Await

case class Book(id: Int, author: String, title: String)

trait BookJsonProtocol extends DefaultJsonProtocol {
  implicit val bookFormat = jsonFormat3(Book)
}

class RouteDSLSpec extends WordSpec with Matchers with ScalatestRouteTest with BookJsonProtocol {
  import RouteDSLSpec._

  "A digital library backend" should {
    "return all the books in the library" in {
      Get("/api/book") ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK

        entityAs[List[Book]] shouldBe books
      }
    }

    "return a book by hitting query param endpoint" in {
      Get("/api/book?id=2") ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Option[Book]] shouldBe Some(Book(2, "JRR Tolkien", "The Lord of the Rings"))
      }
    }

    "return a book by calling the endpoint with id in the path" in {
      Get("/api/book/2") ~> libraryRoute ~> check {
        response.status shouldBe StatusCodes.OK

        val strictEntityFuture = response.entity.toStrict(1 second)
        val strictEntity = Await.result(strictEntityFuture, 1 second)

        strictEntity.contentType shouldBe ContentTypes.`application/json`

        val book = strictEntity.data.utf8String.parseJson.convertTo[Option[Book]]
        book shouldBe Some(Book(2, "JRR Tolkien", "The Lord of the Rings"))
      }
    }

    "insert a book into the database" in {
      val newBook = Book(5, "Steven Pressfield", "The War of Art")
      Post("/api/book", newBook) ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK
        books should contain(newBook)
      }
    }

    "not accept other methods than POST and GET" in {
      Delete("/api/book") ~> libraryRoute ~> check {
        rejections should not be empty

        val methodRejections = rejections.collect {
          case rejection: MethodRejection => rejection
        }
        methodRejections.length shouldBe 2
      }
    }

    "return all books for an author" in {
      Get("/api/book/author/JRR%20Tolkien") ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK
        entityAs[List[Book]] shouldBe books.filter(_.author == "JRR Tolkien")
      }
    }
  }


}

object RouteDSLSpec extends BookJsonProtocol with SprayJsonSupport {

  var books = List(
    Book(1, "Harper Lee", "To Kill a Mockingbird"),
    Book(2, "JRR Tolkien", "The Lord of the Rings"),
    Book(3, "GRR Martin", "A Song of Ice and Fire"),
    Book(4, "Tony Robbins", "Awaken the Giant Within")
  )

  val libraryRoute: Route =
    pathPrefix("api" / "book") {
      (path("author" / Segment) & get) { author =>
        complete(books.filter(_.author == author))
      } ~
      get {
        (path(IntNumber) | parameter('id.as[Int])) { id =>
          complete(books.find(_.id == id))
        } ~
          pathEndOrSingleSlash {
            complete(books)
          }
      } ~
        post {
          entity(as[Book]) { book =>
            books = books :+ book
              complete(StatusCodes.OK)
          } ~
            complete(StatusCodes.BadRequest)
        }
    }
}
