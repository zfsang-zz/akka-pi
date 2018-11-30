import akka.actor._
import akka.routing.RoundRobinRouter
import scala.concurrent.duration.Duration
import scala.concurrent.duration._


sealed trait PiMessage
case object Calculate extends PiMessage
case class Work(start: Int, nrOfElements: Int) extends PiMessage
case class Result(value: Double) extends PiMessage
case class PiApproximation(pi: Double, duration: Duration)

//  based on https://doc.akka.io/docs/akka/2.0.1/intro/getting-started-first-scala.html

// formula: \sum_{n=0}^{\inf} {(-1)^n}/{2n +1) = 1 - 1/3 + 1/5 - 1/7 + ... = \pi/4

object Pi extends App {

  calculate(nrOfWorkers = 8, nrOfElements = 100000, nrOfMessages = 10000)

  class Worker extends Actor {
    // calculatePiFor
    def calculatePiFor(start: Int, nrOfElements: Int): Double = {
      var acc = 0.0
      for (i <- start until (start + nrOfElements))
        acc += 4.0 * (1 - (i % 2) * 2) / (2 * i + 1)
      acc
    }


    def receive: PartialFunction[Any, Unit] = {

      case Work(start, nrOfElements) =>
        sender ! Result(calculatePiFor(start, nrOfElements))

    }
  }

  class Master(nrOfWorkers: Int, nrOfMessages: Int, nrOfElements: Int, listener: ActorRef) extends Actor {
    var pi: Double = _
    var nrOfResults: Int = _
    val start: Long = System.currentTimeMillis

    val workerRouter: ActorRef = context.actorOf(Props[Worker].withRouter(RoundRobinRouter(nrOfWorkers)), name = "workerRouter")

    def receive = {
      case Calculate =>
        for (i <- 0 until nrOfMessages) workerRouter ! Work(i * nrOfElements, nrOfElements)
      case Result(value) =>
        pi += value
        nrOfResults += 1
        if (nrOfResults == nrOfMessages) {
          //Send the result to the listener
          listener ! PiApproximation(pi, duration = (System.currentTimeMillis - start).millis)

          // stops this actor and all its supervised children
          context.stop(self)
        }
    }
  }

  class Listener extends Actor {
    def receive = {
      case PiApproximation(pi, duration) =>
        println("\n\t Pi approximation: \t\t%s\n\t Calculatioin time: \t%s".format(pi, duration))
        context.system.shutdown()
    }
  }

  def calculate(nrOfWorkers: Int, nrOfElements: Int, nrOfMessages: Int): Unit = {
    // Create an Akka system
    val system = ActorSystem("PiSystem")

    // create the result listener, which will print the result and shutdown the system
    val listener = system.actorOf(Props[Listener], name = "listener")

    // create the master
    val master = system.actorOf(Props(new Master(
      nrOfWorkers, nrOfMessages, nrOfElements, listener
    )),
      name ="master")

    // start the calculation
    master ! Calculate
  }

}