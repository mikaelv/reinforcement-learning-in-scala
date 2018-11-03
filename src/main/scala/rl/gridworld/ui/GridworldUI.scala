package rl.gridworld.ui

import org.scalajs.dom
import org.scalajs.dom.html.{Button, Canvas}
import rl.core.{ActionResult, AgentBehaviour, Environment, QLearning}
import rl.gridworld.core.GridworldProblem
import rl.gridworld.core.GridworldProblem.{AgentLocation, Move}

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.util.Random

@JSExportTopLevel("GridworldUI")
object GridworldUI {

  sealed trait UIState
  case object Idle     extends UIState
  case object Stepping extends UIState
  case object Running  extends UIState

  private val initialState: AgentLocation =
    AgentLocation(Random.nextInt(5), Random.nextInt(5))

  private val initialAgentData: QLearning[AgentLocation, Move] =
    QLearning(α = 0.1, γ = 0.9, ε = 0.5, epsilonDecay = 0.999, Q = Map.empty)

  private val env: Environment[AgentLocation, Move] = implicitly
  private val agentBehaviour: AgentBehaviour[QLearning[AgentLocation, Move], AgentLocation, Move] =
    implicitly

  @JSExport
  def main(document: dom.Document,
           canvas: Canvas,
           stepButton: Button,
           runButton: Button,
           pauseButton: Button): Unit = {
    var uiState: UIState = Idle

    var agentData    = initialAgentData
    var currentState = initialState

    def step(): Unit = {
      val (nextAction, updateAgent) =
        agentBehaviour.chooseAction(agentData, currentState, GridworldProblem.validActions)
      val (nextState, reward) = env.step(currentState, nextAction)

      agentData = updateAgent(ActionResult(reward, nextState))
      currentState = nextState

      updateUI(document, canvas, agentData, currentState)
    }

    def tick(): Unit = uiState match {
      case Idle =>
        updateUI(document, canvas, agentData, currentState)

      case Stepping =>
        step()
        uiState = Idle

      case Running =>
        step()
    }

    stepButton.onclick = _ => uiState = Stepping
    runButton.onclick = _ => uiState = Running
    pauseButton.onclick = _ => uiState = Idle

    dom.window.setInterval(() => tick(), 150)
  }

  private def updateUI(document: dom.Document,
                       canvas: Canvas,
                       agentData: QLearning[AgentLocation, Move],
                       agentLocation: AgentLocation): Unit = {
    val ctx = canvas
      .getContext("2d")
      .asInstanceOf[dom.CanvasRenderingContext2D]

    val cellWidth  = canvas.width / 5
    val cellHeight = canvas.height / 5

    ctx.clearRect(0, 0, canvas.width, canvas.height)

    ctx.fillStyle = "black"
    ctx.lineWidth = 1
    ctx.font = "30px arial"

    for (i <- 0 until 5) {
      for (j <- 0 until 5) {
        ctx.strokeRect(i * cellWidth, j * cellHeight, cellWidth, cellHeight)
      }
    }

    ctx.fillText("A", cellWidth + 10, 30)
    ctx.fillText("B", 3 * cellWidth + 10, 30)
    ctx.fillText("A'", cellWidth + 10, 4 * cellHeight + 30)
    ctx.fillText("B'", 3 * cellWidth + 10, 2 * cellHeight + 30)
    drawArrow(ctx, cellWidth + 20, 50, 4 * cellHeight - 10, "+10")
    drawArrow(ctx, 3 * cellWidth + 20, 50, 2 * cellHeight - 10, "+5")

    ctx.fillStyle = "red"

    ctx.beginPath()
    ctx.arc((agentLocation.x + 0.5) * cellWidth,
            (agentLocation.y + 0.5) * cellHeight,
            0.2 * cellWidth,
            0,
            2 * Math.PI)
    ctx.fill()
    ctx.closePath()

    updateTable(document, agentData.Q)
    updateEpsilon(document, agentData.ε)
  }

  private def drawArrow(ctx: dom.CanvasRenderingContext2D,
                        x: Int,
                        fromY: Int,
                        toY: Int,
                        text: String): Unit = {
    val headLength = 10

    ctx.beginPath()
    ctx.lineWidth = 2
    ctx.moveTo(x, fromY)
    ctx.lineTo(x, toY)
    ctx.lineTo(x - headLength * Math.cos(Math.PI / 3), toY - headLength * Math.sin(Math.PI / 3))
    ctx.moveTo(x, toY)
    ctx.lineTo(x - headLength * Math.cos(2 * Math.PI / 3),
               toY - headLength * Math.sin(2 * Math.PI / 3))
    ctx.stroke()
    ctx.closePath()

    ctx.fillText(text, x + 5, (toY + fromY) / 2 + 5)
  }

  private def updateTable(document: dom.Document,
                          Q: Map[AgentLocation, Map[Move, Double]]): Unit = {
    for {
      x <- 0 to 4
      y <- 0 to 4
    } {
      val actionValues = Q.getOrElse(AgentLocation(x, y), Map.empty)

      val text = {
        val descendingActionValues = actionValues.groupBy(_._2).toList.sortBy(_._1).reverse
        if (descendingActionValues.length < 2) {
          "??"
        } else {
          descendingActionValues.head._2.map(_._1.toString.head).toList.sorted.mkString
        }
      }

      val id = s"${x}_$y"
      document.getElementById(id).innerHTML = text
    }
  }

  private def updateEpsilon(document: dom.Document, ε: Double): Unit = {
    document.getElementById("epsilon").innerHTML = ε.toString
  }

}