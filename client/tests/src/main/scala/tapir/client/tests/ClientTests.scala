package tapir.client.tests

import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeBuilder
import tapir._
import tapir.tests._
import tapir.typelevel.ParamsAsArgs

import scala.util.Random

trait ClientTests extends FunSuite with Matchers with BeforeAndAfterAll {

  testClient(endpoint, (), Right(()))
  testClient(singleQueryParam, "value1", Right("param1: value1"))
  testClient(twoPathParams, ("v1", 10), Right("v1 10 None"))
  testClient(singleBody, "test", Right("test"))
  testClient(singleMappedValue, "value1".toList, Right("param1: value1"))
  testClient(twoMappedValues, StringInt("v1", 10), Right("v1 10 None"))
  testClient(twoMappedValuesAndUnmapped, (StringInt("v1", 10), "p1"), Right("v1 10 Some(p1)"))
  testClient(singleOutMappedValue, "value1", Right("param1: value1".toList))
  testClient(twoOutMappedValues, "value1", Right(StringInt("param1: value1", 6)))

  //

  private object param1 extends QueryParamDecoderMatcher[String]("param1")
  private object param1Opt extends OptionalQueryParamDecoderMatcher[String]("param1")

  private val service = HttpService[IO] {
    case GET -> Root :? param1(v)                                => Ok(s"param1: $v", Header("test-header", v.length.toString))
    case GET -> Root / "api" / v1 / "user" / v2 :? param1Opt(p1) => Ok(s"$v1 $v2 $p1")
    case r @ POST -> Root / "echo" / "body"                      => r.as[String].flatMap(Ok(_))
    case GET -> Root                                             => Ok()
  }

  //

  type Port = Int

  def send[I, E, O, FN[_]](e: Endpoint[I, E, O], port: Port, args: I)(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): IO[Either[E, O]]

  def testClient[I, E, O, FN[_]](e: Endpoint[I, E, O], args: I, expectedResult: Either[E, O])(
      implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): Unit = {

    test(e.show)(send(e, port, args).unsafeRunSync() shouldBe expectedResult)
  }

  private var port: Port = _
  private var server: Server[IO] = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    port = randomPort()
    server = BlazeBuilder[IO]
      .bindHttp(port)
      .mountService(service)
      .start
      .unsafeRunSync()
  }

  override protected def afterAll(): Unit = {
    server.shutdownNow()
    super.afterAll()
  }

  //

  private val random = new Random()
  private def randomPort(): Port = random.nextInt(29232) + 32768
}