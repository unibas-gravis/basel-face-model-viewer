/*
 * Copyright University of Basel, Graphics and Vision Research Group
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
package faces.apps

import java.awt.Dimension
import java.awt.event.{ActionEvent, ActionListener}
import java.io.File
import javax.swing._
import javax.swing.event.{ChangeEvent, ChangeListener}
import javax.swing.text.NumberFormatter
import java.text.NumberFormat

import breeze.linalg.min
import scalismo.faces.gui.{GUIBlock, GUIFrame, ImagePanel}
import scalismo.faces.gui.GUIBlock._
import scalismo.faces.parameters.RenderParameter
import scalismo.faces.io.RenderParameterIO
import scalismo.faces.sampling.face.MoMoRenderer
import scalismo.faces.color.{RGB, RGBA}
import scalismo.faces.image.PixelImage
import scalismo.faces.io.MoMoIO
import scalismo.utils.Random
import spire.syntax.field

import scala.reflect.io.Path
import scala.util.{Failure, Success}

object ModelViewer extends App {

  // check if args(0):
  // - is file, try to load it
  // - is directory, open file chooser there
  // - open file chooser at default location

  final val DEFAULT_DIR = new File(".")

  val modelFile: Option[File] = if (args.size>0) {
    val arg = args(0)
    val path = Path(arg)
    if ( path.isFile ) Some(path.jfile)
    else {
      val dir = if ( path.isDirectory ) {
        path.jfile
      } else {
        DEFAULT_DIR
      }
      askUserForModelFile(dir)
    }
  } else {
    askUserForModelFile(DEFAULT_DIR)
  }

  modelFile.map(SimpleModelViewer(_))


  def askUserForModelFile(dir: File): Option[File] = {
    val jFileChooser = new JFileChooser(dir)
    if (jFileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
      Some(jFileChooser.getSelectedFile())
    } else {
      println("No model select...")
      None
    }
  }
}

case class SimpleModelViewer(
  modelFile: File,
  imageWidth: Int = 512,
  imageHeight: Int = 512,
  maximalSliderValue: Int = 2,
  maximalShapeRank: Option[Int] = None,
  maximalColorRank: Option[Int] = None,
  maximalExpressionRank: Option[Int] = None
) {

  scalismo.initialize()
  val seed = 1024L
  implicit val rnd = new Random(seed)


  val model = MoMoIO.read(modelFile, "").get

  val shapeRank = maximalShapeRank match {
    case Some(rank) => min(model.neutralModel.shape.rank, rank)
    case _ => model.neutralModel.shape.rank
  }

  val colorRank = maximalColorRank match {
    case Some(rank) => min(model.neutralModel.color.rank, rank)
    case _ => model.neutralModel.color.rank
  }

  val expRank = maximalExpressionRank match {
    case Some(rank) => try{min(model.expressionModel.get.expression.rank, rank)} catch {case e: Exception => 0}
    case _ => try{model.expressionModel.get.expression.rank} catch {case e: Exception => 0}
  }

  val renderer = MoMoRenderer(model, RGBA.BlackTransparent).cached(5)

  val initDefault: RenderParameter = RenderParameter.defaultSquare.fitToImageSize(imageWidth, imageHeight)
  val init10 = initDefault.copy(
    momo = initDefault.momo.withNumberOfCoefficients(shapeRank, colorRank, expRank)
  )
  var init = init10

  var changingSliders = false

  val sliderSteps = 1000
  var maximalSigma: Int = maximalSliderValue
  var maximalSigmaSpinner = {
    val spinner = new JSpinner(new SpinnerNumberModel(maximalSigma,0,999,1))
    spinner.addChangeListener( new ChangeListener() {
      override def stateChanged(e: ChangeEvent) = {
        val newMaxSigma = spinner.getModel().asInstanceOf[SpinnerNumberModel].getNumber.intValue()
        maximalSigma = math.abs(newMaxSigma)
        setShapeSliders
        setColorSliders
        setExpSliders
      }
    })
    spinner.setToolTipText("maximal slider value")
    spinner
  }


  def sliderToParam(value: Int): Double = {
    maximalSigma * value.toDouble/sliderSteps
  }

  def paramToSlider(value: Double): Int = {
    (value / maximalSigma * sliderSteps).toInt
  }

  val bg = PixelImage(imageWidth, imageHeight, (_, _) => RGBA.Black)

  val imageWindow = ImagePanel(renderWithBG(init))

  //--- SHAPE -----
  val shapeSlider: IndexedSeq[JSlider] = for (n <- 0 until shapeRank) yield {
    GUIBlock.slider(-sliderSteps, sliderSteps, 0, f => {
      updateShape(n, f)
      updateImage()
    })
  }

  val shapeSliderView = GUIBlock.shelf(shapeSlider.zipWithIndex.map(s => GUIBlock.stack(s._1, new JLabel("" + s._2))): _*)
  val shapeScrollPane = new JScrollPane(shapeSliderView)
  val shapeScrollBar = shapeScrollPane.createVerticalScrollBar()
  shapeScrollPane.setSize(800, 300)
  shapeScrollPane.setPreferredSize(new Dimension(800, 300))

  val rndShapeButton = GUIBlock.button("random", {
    randomShape(); updateImage()
  })
  val resetShapeButton = GUIBlock.button("reset", {
    resetShape(); updateImage()
  })
  rndShapeButton.setToolTipText("draw each shape parameter at random from a standard normal distribution")
  resetShapeButton.setToolTipText("set all shape parameters to zero")

  def updateShape(n: Int, value: Int): Unit = {
    init = init.copy(momo = init.momo.copy(shape = {
      val current = init.momo.shape
      current.zipWithIndex.map { case (v, i) => if (i == n) sliderToParam(value) else v }
    }))
  }

  def randomShape() = {
    init = init.copy(momo = init.momo.copy(shape = {
      val current = init.momo.shape
      current.zipWithIndex.map {
        case (v, i) =>
          rnd.scalaRandom.nextGaussian
      }

    }))
    setShapeSliders()
  }

  def resetShape() = {
    init = init.copy(momo = init.momo.copy(
      shape = IndexedSeq.fill(shapeRank)(0.0)
    ))
    setShapeSliders()
  }

  def setShapeSliders() = {
    changingSliders = true
    (0 until shapeRank).foreach(i => {
      shapeSlider(i).setValue(paramToSlider(init.momo.shape(i)))
    })
    changingSliders = false
  }

  //--- COLOR -----
  val colorSlider: IndexedSeq[JSlider] = for (n <- 0 until colorRank) yield {
    GUIBlock.slider(-sliderSteps, sliderSteps, 0, f => {
      updateColor(n, f)
      updateImage()
    })
  }

  val colorSliderView = GUIBlock.shelf(colorSlider.zipWithIndex.map(s => GUIBlock.stack(s._1, new JLabel("" + s._2))): _*)
  val colorScrollPane = new JScrollPane(colorSliderView)
  val colorScrollBar = colorScrollPane.createHorizontalScrollBar()
  colorScrollPane.setSize(800, 300)
  colorScrollPane.setPreferredSize(new Dimension(800, 300))

  val rndColorButton = GUIBlock.button("random", {
    randomColor(); updateImage()
  })

  val resetColorButton = GUIBlock.button("reset", {
    resetColor(); updateImage()
  })
  rndColorButton.setToolTipText("draw each color parameter at random from a standard normal distribution")
  resetColorButton.setToolTipText("set all color parameters to zero")

  def updateColor(n: Int, value: Int): Unit = {
    init = init.copy(momo = init.momo.copy(color = {
      val current = init.momo.color
      current.zipWithIndex.map { case (v, i) => if (i == n) sliderToParam(value) else v }
    }))
  }

  def randomColor() = {
    init = init.copy(momo = init.momo.copy(color = {
      val current = init.momo.color
      current.zipWithIndex.map {
        case (v, i) =>
          rnd.scalaRandom.nextGaussian
      }

    }))
    setColorSliders()
  }

  def resetColor() = {
    init = init.copy(momo = init.momo.copy(
      color = IndexedSeq.fill(colorRank)(0.0)
    ))
    setColorSliders()
  }

  def setColorSliders() = {
    changingSliders = true
    (0 until colorRank).foreach(i => {
      colorSlider(i).setValue(paramToSlider(init.momo.color(i)))
    })
    changingSliders = false
  }

  //--- EXPRESSION -----
  val expSlider: IndexedSeq[JSlider] = for (n <- 0 until expRank)yield {
    GUIBlock.slider(-sliderSteps, sliderSteps, 0, f => {
      updateExpression(n, f)
      updateImage()
    })
  }

  val expSliderView = GUIBlock.shelf(expSlider.zipWithIndex.map(s => GUIBlock.stack(s._1, new JLabel("" + s._2))): _*)
  val expScrollPane = new JScrollPane(expSliderView)
  val expScrollBar = expScrollPane.createVerticalScrollBar()
  expScrollPane.setSize(800, 300)
  expScrollPane.setPreferredSize(new Dimension(800, 300))

  val rndExpButton = GUIBlock.button("random", {
    randomExpression(); updateImage()
  })
  val resetExpButton = GUIBlock.button("reset", {
    resetExpression(); updateImage()
  })

  rndExpButton.setToolTipText("draw each expression parameter at random from a standard normal distribution")
  resetExpButton.setToolTipText("set all expression parameters to zero")

  def updateExpression(n: Int, value: Int): Unit = {
    init = init.copy(momo = init.momo.copy(expression = {
      val current = init.momo.expression
      current.zipWithIndex.map { case (v, i) => if (i == n) sliderToParam(value) else v }
    }))
  }

  def randomExpression() = {
    init = init.copy(momo = init.momo.copy(expression = {
      val current = init.momo.expression
      current.zipWithIndex.map {
        case (v, i) =>
          rnd.scalaRandom.nextGaussian
      }

    }))
    setExpSliders()
  }

  def resetExpression() = {
    init = init.copy(momo = init.momo.copy(
      expression = IndexedSeq.fill(expRank)(0.0)
    ))
    setExpSliders()
  }

  def setExpSliders() = {
    changingSliders = true
    (0 until expRank).foreach(i => {
      expSlider(i).setValue(paramToSlider(init.momo.expression(i)))
    })
    changingSliders = false
  }


  //--- ALL TOGETHER -----
  val randomButton = GUIBlock.button("random", {
    randomShape(); randomColor(); randomExpression(); updateImage()
  })
  val resetButton = GUIBlock.button("reset", {
    resetShape(); resetColor(); resetExpression(); updateImage()
  })

  randomButton.setToolTipText("draw each model parameter at random from a standard normal distribution")
  resetButton.setToolTipText("set all model parameters to zero")

  //loads parameters from file
  //TODO: load other parameters than the momo shape, expr and color

  def askUserForRPSFile(dir: File): Option[File] = {
    val jFileChooser = new JFileChooser(dir)
    if (jFileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
      Some(jFileChooser.getSelectedFile())
    } else {
      println("No Parameters select...")
      None
    }
  }

  def resizeParameterSequence(params: IndexedSeq[Double], length: Int, fill: Double): IndexedSeq[Double] = {
    val zeros = IndexedSeq.fill[Double](length)(fill)
    (params ++ zeros).slice(0, length) //brute force
  }

  def updateModelParameters(params: RenderParameter) = {
    val newShape = resizeParameterSequence(params.momo.shape, shapeRank, 0)
    val newColor = resizeParameterSequence(params.momo.color, colorRank, 0)
    val newExpr = resizeParameterSequence(params.momo.expression, expRank, 0)
    println("Loaded Parameters")

    init = init.copy(momo = init.momo.copy(shape = newShape, color = newColor, expression = newExpr))
    setShapeSliders()
    setColorSliders()
    setExpSliders()
    updateImage()
  }

  val loadButton= GUIBlock.button(
    "load RPS",
    {
      for {rpsFile <- askUserForRPSFile(new File("."))
           rpsParams <- RenderParameterIO.read(rpsFile)} {
        val maxSigma = (rpsParams.momo.shape ++ rpsParams.momo.color ++ rpsParams.momo.expression).map(math.abs(_)).max
        if ( maxSigma > maximalSigma ) {
          maximalSigma = math.ceil(maxSigma).toInt
          maximalSigmaSpinner.setValue(maximalSigma)
          setShapeSliders()
          setColorSliders()
          setExpSliders()
        }
        updateModelParameters(rpsParams)
      }
    }
  )


  //---- update the image
  def updateImage(): Unit = {
    if (!changingSliders)
      imageWindow.updateImage(renderWithBG(init))
  }

  def renderWithBG(init: RenderParameter): PixelImage[RGB] = {
    val fg = renderer.renderImage(init)
    fg.zip(bg).map { case (f, b) => b.toRGB.blend(f) }
    //    fg.map(_.toRGB)
  }

  //--- COMPOSE FRAME ------
  val controls = new JTabbedPane()
  controls.addTab("color", GUIBlock.stack(colorScrollPane, GUIBlock.shelf(rndColorButton, resetColorButton)))
  controls.addTab("shape", GUIBlock.stack(shapeScrollPane, GUIBlock.shelf(rndShapeButton, resetShapeButton)))
  controls.addTab("expression", GUIBlock.stack(expScrollPane, GUIBlock.shelf(rndExpButton, resetExpButton)))

  val guiFrame: GUIFrame = GUIBlock.stack(
    GUIBlock.shelf(imageWindow, GUIBlock.stack(controls, GUIBlock.shelf(maximalSigmaSpinner, randomButton, resetButton, loadButton)))
  ).displayInNewFrame("MoMo-Viewer")



  //--- ROTATION CONTROLS ------

  import java.awt.event._

  var lookAt = false
  imageWindow.requestFocusInWindow()

  imageWindow.addKeyListener(new KeyListener {
    override def keyTyped(e: KeyEvent): Unit = {
    }

    override def keyPressed(e: KeyEvent): Unit = {
      if (e.getKeyCode == KeyEvent.VK_CONTROL) lookAt = true
    }

    override def keyReleased(e: KeyEvent): Unit = {
      if (e.getKeyCode == KeyEvent.VK_CONTROL) lookAt = false
    }
  })

  imageWindow.addMouseListener(new MouseListener {
    override def mouseExited(e: MouseEvent): Unit = {}

    override def mouseClicked(e: MouseEvent): Unit = {
      imageWindow.requestFocusInWindow()
    }

    override def mouseEntered(e: MouseEvent): Unit = {}

    override def mousePressed(e: MouseEvent): Unit = {}

    override def mouseReleased(e: MouseEvent): Unit = {}
  })

  imageWindow.addMouseMotionListener(new MouseMotionListener {
    override def mouseMoved(e: MouseEvent): Unit = {
      if (lookAt) {
        val x = e.getX
        val y = e.getY
        val yawPose = math.Pi / 2 * (x - imageWidth * 0.5).toDouble / (imageWidth / 2)
        val pitchPose = math.Pi / 2 * (y - imageHeight * 0.5).toDouble / (imageHeight / 2)

        init = init.copy(pose = init.pose.copy(yaw = yawPose, pitch = pitchPose))
        updateImage()
      }
    }

    override def mouseDragged(e: MouseEvent): Unit = {}
  })

}
