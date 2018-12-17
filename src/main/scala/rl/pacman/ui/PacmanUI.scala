package rl.pacman.ui

import org.scalajs.dom
import org.scalajs.dom.html
import rl.core._
import rl.pacman.core.PacmanProblem._
import rl.pacman.training.QKeyValue

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.js.typedarray.{ArrayBuffer, TypedArrayBuffer, Uint8Array}
import scala.util.{Failure, Success}

@JSExportTopLevel("PacmanUI")
object PacmanUI {

  sealed trait UIState

  case object Running extends UIState

  case class GameOver(flashesLeft: Int) extends UIState

  private val env: Environment[GameState, Move] = implicitly
  private val stateConversion: StateConversion[GameState, AgentState] = implicitly
  private val agentBehaviour: AgentBehaviour[QLearning[AgentState, Move], AgentState, Move] =
    implicitly

  @JSExport
  def main(document: dom.Document, canvas: html.Canvas, info: html.Div): Unit = {
    loadQ(info) onComplete {
      case Failure(e) =>
        info.innerHTML = "Failed to load Q data."
        println(e)
      case Success(q) =>
        info.innerHTML = "Loaded Q data."

        val initialAgentData: QLearning[AgentState, Move] =
          QLearning(α = 0.1, γ = 0.9, ε = 0.0, Q = q)

        var agentData = initialAgentData
        var gameState: GameState = initialState
        var lastAction: Move = Move.Left
        var uiState: UIState = Running

        def step(): Unit = uiState match {
          case Running =>
            val currentState = stateConversion.convertState(gameState)
            val possibleActions = env.possibleActions(gameState)
            val (chosenAction, updateAgent) =
              agentBehaviour.chooseAction(agentData, currentState, possibleActions)
            val (nextState, reward) = env.step(gameState, chosenAction)

            agentData = updateAgent(ActionResult(reward, stateConversion.convertState(nextState)))
            gameState = nextState
            lastAction = chosenAction

            drawGame(canvas, gameState, lastAction)

            if (env.isTerminal(gameState)) {
              uiState = GameOver(flashesLeft = 5)
            }
          case GameOver(0) =>
            gameState = initialState
            uiState = Running
          case GameOver(n) =>
            if (n % 2 == 0)
              drawGame(canvas, gameState, lastAction)
            else
              drawBlankCanvas(canvas)
            uiState = GameOver(n - 1)
        }

        dom.window.setInterval(() => step(), 200)
    }

  }

  private def loadQBin(info: html.Div): Future[Map[AgentState, Map[Move, Double]]] = {
    info.innerHTML = "Downloading Q-values binary file..."

    dom.ext.Ajax.get("data/pacman/Q.bin").flatMap { r =>
      info.innerHTML = "Parsing Q-values binary file..."

      Future {
        import upickle.default._
        println("casting")
        implicit val wtf: js.Dynamic = scala.scalajs.js.Dynamic.global
        val buffer = r.response.asInstanceOf[ArrayBuffer]
        println("wrapping")
        val byteArray = TypedArrayBuffer.wrap(buffer).asReadOnlyBuffer()

        println("reading " + byteArray.get())

        // TODO the array method is not implemented, but the others seem to be
        val decoded = readBinary[List[QKeyValue]](byteArray.array())
        println("mapping")
        decoded.map(kv => (kv.key, kv.value)).toMap
      }

    }
  }

  private def loadQ(info: html.Div): Future[Map[AgentState, Map[Move, Double]]] = {
    info.innerHTML = "Downloading Q-values JSON file..."

    dom.ext.Ajax.get("data/pacman/Q.json").flatMap { r =>
      info.innerHTML = "Parsing Q-values JSON file..."

      import io.circe.parser._
      val either = for {
        json <- parse(r.responseText)
        decoded <- json.as[List[QKeyValue]]
      } yield {
        decoded.map(kv => (kv.key, kv.value)).toMap
      }

      Future.fromTry(either.toTry)
    }
  }

  private def drawGame(canvas: html.Canvas, state: GameState, actionTaken: Move): Unit = {
    val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
    val pixelSize = 50
    val pixelCentre = pixelSize / 2

    def drawEye(ghost: Location, xOffset: Int): Unit = {
      ctx.save()

      ctx.translate(ghost.x * pixelSize + pixelCentre + xOffset,
        ghost.y * pixelSize + pixelCentre - 10)
      ctx.scale(1.0, 1.5)

      ctx.beginPath()
      ctx.arc(0.0, 0.0, 4, 0.0, Math.PI * 2.0)
      ctx.fillStyle = "white"
      ctx.fill()
      ctx.closePath()

      ctx.beginPath()
      ctx.arc(0.0, 0.0, 2, 0.0, Math.PI * 2.0)
      ctx.fillStyle = "black"
      ctx.fill()
      ctx.closePath()

      ctx.restore()
    }

    def drawGhost(ghost: Location, colour: String): Unit = {
      // body
      ctx.beginPath()
      ctx.fillStyle = colour
      ctx.arc(ghost.x * pixelSize + pixelCentre,
        ghost.y * pixelSize + pixelCentre,
        20,
        0.0,
        Math.PI * 2.0)
      ctx.fill()
      ctx.closePath()

      // left eye
      drawEye(ghost, -10)

      // right eye
      drawEye(ghost, 10)
    }

    ctx.fillStyle = "black"
    ctx.fillRect(0, 0, 1000, 350)

    // draw walls
    for (wall <- walls) {
      ctx.fillStyle = "blue"
      ctx.fillRect(wall.x * pixelSize + 10, wall.y * pixelSize + 10, pixelSize - 20, pixelSize - 20)
    }

    // draw food
    for (food <- state.food) {
      ctx.beginPath()
      ctx.fillStyle = "yellow"
      ctx.arc(food.x * pixelSize + pixelCentre,
        food.y * pixelSize + pixelCentre,
        5,
        0.0,
        Math.PI * 2.0)
      ctx.fill()
      ctx.closePath()
    }

    // draw pills
    for (pill <- state.pills) {
      ctx.beginPath()
      ctx.fillStyle = "yellow"
      ctx.arc(pill.x * pixelSize + pixelCentre,
        pill.y * pixelSize + pixelCentre,
        15,
        0.0,
        Math.PI * 2.0)
      ctx.fill()
      ctx.closePath()
    }

    // draw ghosts
    state.mode match {
      case Mode.Normal =>
        drawGhost(state.ghost1, "green")
        drawGhost(state.ghost2, "red")
      case Mode.ChaseGhosts(_) =>
        drawGhost(state.ghost1, "cyan")
        drawGhost(state.ghost2, "cyan")
    }

    // draw pacman
    val (startAngle, endAngle) = actionTaken match {
      case Move.Left => (Math.PI * -0.8, Math.PI * 0.8)
      case Move.Up => (Math.PI * -0.3, Math.PI * 1.3)
      case Move.Right => (Math.PI * 0.2, Math.PI * 1.8)
      case Move.Down => (Math.PI * 0.7, Math.PI * 2.3)
    }
    ctx.beginPath()
    ctx.fillStyle = "yellow"
    ctx.arc(state.pacman.x * pixelSize + pixelCentre,
      state.pacman.y * pixelSize + pixelCentre,
      20.0,
      startAngle,
      endAngle)
    ctx.lineTo(state.pacman.x * pixelSize + pixelCentre, state.pacman.y * pixelSize + pixelCentre)
    ctx.closePath()
    ctx.fill()
  }

  private def drawBlankCanvas(canvas: html.Canvas): Unit = {
    val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
    ctx.fillStyle = "black"
    ctx.fillRect(0, 0, 1000, 350)
  }

}
