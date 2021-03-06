package io.swagger.client

import akka.dispatch.{Promise, Await, Future, ExecutionContext}
import akka.util.duration._
import akka.util.FiniteDuration
import java.net.URI
import java.util.concurrent.Executors

/**
 * A trait for a load balancing strategy.
 * It takes a set of hosts and returns a single host
 * from the Set
 */
trait HostPicker {
  /**
   * Pick a host from the provided list of services
   * @param hosts The hosts to pick from
   * @return A Future with an Option that contains the host if there was one.
   */
  def apply(hosts: Set[String])(implicit executionContext: ExecutionContext): Future[Option[String]]
}

object HeadHostPicker extends HostPicker {
  def apply(hosts: Set[String])(implicit executionContext: ExecutionContext): Future[Option[String]] = {
    Future(hosts.headOption)
  }
}

object RandomHostPicker extends HostPicker {
  private[this] val rand = new util.Random()
  def apply(hosts: Set[String])(implicit executionContext: ExecutionContext): Future[Option[String]] = {
    Future(if (hosts.nonEmpty) Some(hosts.toList(rand.nextInt(hosts.size))) else None)
  }
}

trait ServiceLocator {
  implicit protected def executionContext: ExecutionContext
  def locate(name: String): Future[Set[String]]
  def locateBlocking(name: String, atMost: FiniteDuration = 20 seconds): Set[String] =
    Await.result(locate(name), atMost)

  def pickOne(name: String, picker: HostPicker = RandomHostPicker): Future[Option[String]]

  def pickOneBlocking(name: String, picker: HostPicker = RandomHostPicker, atMost: FiniteDuration = 20 seconds): Option[String] =
    Await.result(pickOne(name, picker), atMost)

  def locateAsUris(name: String, path: String): Future[Set[String]]
  def locateAsUrisBlocking(name: String, path: String, atMost: FiniteDuration = 20 seconds): Set[String] =
    Await.result(locateAsUris(name, path), atMost)

  def pickOneAsUri(name: String, path: String, picker: HostPicker = RandomHostPicker): Future[Option[String]]

  def pickOneAsUriBlocking(name: String, path: String, picker: HostPicker = RandomHostPicker, atMost: FiniteDuration = 20 seconds): Option[String] =
    Await.result(pickOneAsUri(name, path, picker), atMost)

}

object GlobalContext {
  lazy val executionContext: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

}

case class BaseUrl(url: String)(implicit protected val executionContext: ExecutionContext = GlobalContext.executionContext) extends ServiceLocator {
  private[this] val uri = URI.create(url)
  private[this] val withoutScheme = uri.getHost
  private[this] val withScheme = uri.getScheme + "://" + uri.getAuthority + stripTrailingSlash(uri.getPath)


  def locate(name: String): Future[Set[String]] = Promise.successful(Set(withoutScheme)).future

  def pickOne(name: String, picker: HostPicker): Future[Option[String]] = Promise.successful(Some(withoutScheme)).future

  def locateAsUris(name: String, path: String): Future[Set[String]] = Promise.successful(Set(withScheme)).future

  def pickOneAsUri(name: String, path: String, picker: HostPicker): Future[Option[String]] = Promise.successful(Some(withScheme)).future

  private[this] def stripTrailingSlash(s: String): String = if (s endsWith "/") s.dropRight(1) else s
}
