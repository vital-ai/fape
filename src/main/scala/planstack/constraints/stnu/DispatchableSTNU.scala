package planstack.constraints.stnu

import scala.collection.mutable.ListBuffer
import scala.util.Random
import planstack.structures.Converters._

trait DispatchableSTNU[ID] extends ISTNU[ID] {


  /** Mark a controllable var as executed at a given time */
  def executeVar(n:Int, atTime:Int)

  /** Set the occurrence time of a contingent event to a given time */
  def occurred(n:Int, atTime:Int)

  def dispatchableEvents(atTime:Int) : Iterable[Int]
}



class Dispatcher[ID](edg : EDG[ID],
                     todo : ListBuffer[Edge[ID]],
                     isConsistent : Boolean,
                     emptySpots : Set[Int],
                     allConstraints : List[Edge[ID]],
                     dispatchableVars : Set[Int],
                     contingentVars : Set[Int],
                     protected var enabled : Set[Int],
                     protected var executed : Set[Int])
  extends FastIDC[ID](edg, todo, isConsistent, emptySpots, allConstraints, dispatchableVars, contingentVars)
  with DispatchableSTNU[ID]
{

  def this() = this(new EDG[ID], ListBuffer[Edge[ID]](), true, Set(), List(), Set(), Set(), Set(), Set())

  def this(toCopy : Dispatcher[ID]) =
    this(new EDG(toCopy.edg), toCopy.todo.clone(), toCopy.consistent, toCopy.emptySpots,
      toCopy.allConstraints, toCopy.dispatchableVars, toCopy.contingentVars, toCopy.enabled, toCopy.executed)

  def executeVar(u:Int, atTime:Int): Unit = {
    println("Executing   "+hr(u)+"   at time   "+atTime)
    assert(!isExecuted(u))
    assert(dispatchableVars.contains(u))
    assert(latestStart(u) >= atTime)
    assert(isEnabled(u))
    this.enforceInterval(start, u, atTime, atTime)
    executed = executed + u
    enabled = enabled ++ events.filter(isEnabled(_))
    propagate()
  }

  def occurred(u:Int, atTime:Int) = {
    println("Occured:   "+hr(u)+"   at time   "+atTime)
    assert(!isExecuted(u))
    assert(contingentVars.contains(u))
    assert(latestStart(u) >= atTime)
    assert(enabled.contains(u))
    this.removeContingentOn(u)
    this.enforceInterval(start, u, atTime, atTime)
    removeWaitsOn(u)
    executed = executed + u
    enabled = enabled ++ events.filter(isEnabled(_))
    propagate()
  }

  def isExecuted(n:Int) = executed.contains(n)

  def isEnabled(u:Int): Boolean = {
    if(isExecuted(u))
      return false
    for(e <- edg.outNegReq(u)) {
      if((isContingent(e.v) || dispatchableVars.contains(e.v)) && !executed.contains(e.v)) {
        return false
      }
      for(e <- edg.conditionals.outEdges(u)) {
        if(!isExecuted(e.l.node))
          if(earliestStart(e.v) == Int.MaxValue
            || earliestStart(e.v) - e.l.value > earliestStart(u))
            return false
      }
    }
    true
  }

  def isLive(u:Int, currentTime:Int) : Boolean =
    earliestStart(u) <= currentTime

  def removeContingentOn(n:Int): Unit = {
    edg.contingents.deleteEdges((e:E) => {
      e.v == n && e.l.positive || e.u == n && e.l.negative
    })
  }

  def removeWaitsOn(u:Int) = {
    edg.conditionals.deleteEdges((e:E) => e.l.cond && e.l.node == u)
  }

  def contingentEvents(t:Int) : Iterable[(Int,Int)] = {
    for(n <- contingentVars ;
        if !isExecuted(n) ;
        if isLive(n, t) ;
        if t >= latestStart(n) || Random.nextInt(99)<10
    ) yield (n, t)
  }

  override def checkConsistency() = {
    if(todo.nonEmpty) {
      super.checkConsistency()
      edg.apsp()
      isConsistent
    } else {
      super.checkConsistency()
    }
  }

  override def checkConsistencyFromScratch() = {
    super.checkConsistencyFromScratch()
    edg.apsp()
    isConsistent
  }

  def propagate(): Unit = {
    edg.apsp()
    checkConsistencyFromScratch()
    edg.apsp()
  }

  def hr(n:Int) = (n match {
    case 0 => "start"
    case 1 => "end"
    case 2 => "WifeStore"
    case 3 => "StartDriving"
    case 4 => "WifeHome"
    case 5 => "StartCooking"
    case 6 => "DinnerReady"
    case x => x.toString
  })+"("+n+")"

  def getExecuted = executed
  def getLive(t:Int) = events.filter(!executed.contains(_)).filter(isLive(_, t))
  def getEnabled = enabled

  def printAll(t:Int) = {
    println("    Executed: "+getExecuted.map(hr(_)))
    println("    Enabled: "+getEnabled.map(hr(_)))
    println("    Live: "+getLive(t).map(hr(_)))
    for(n <- events)
      println(hr(n)+" "+earliestStart(n)+" "+latestStart(n))
  }

  override def dispatchableEvents(atTime:Int): Iterable[Int] = {
    checkConsistency()
    edg.apsp()
    val executables = dispatchableVars.filter(n =>
      enabled.contains(n) && !executed.contains(n) && isLive(n, atTime))
    executables
  }


  def dispatch(): Unit = {
    enabled = enabled + start
    var currentTIme = 0
    while(executed.size != size && currentTIme < 200) {
//      controllables.filter(!executed.contains(_)).foreach(enforceMinDelay(start,_,currentTIme))
      checkConsistency()
      assert(isConsistent)
//      println("t="+currentTIme)
//      printAll(currentTIme)

      enabled = enabled ++ events.filter(isEnabled(_))
      for((n,t)               <- contingentEvents(currentTIme)) {
        println("Contingent: "+n+" "+t)
        occurred(n, t)
      }


      for(n <- dispatchableEvents(currentTIme))
        executeVar(n, currentTIme)



      currentTIme += 1
    }
  }

  override def earliestStart(u: Int): Int =
    if(u == start) 0
    else -edg.requirements.edgeValue(u, start).value

  override def latestStart(u: Int): Int =
    if(u == 0) 0
    else if(edg.requirements.edgeValue(start,u) == null) Int.MaxValue
    else edg.requirements.edgeValue(start,u).value

  override def cc(): Dispatcher[ID] = new Dispatcher[ID](this)
}

object Main extends App {
  val idc = new Dispatcher[String]()

  val WifeStore = idc.addDispatchable()
  val StartDriving = idc.addContingentVar()
  val WifeHome = idc.addContingentVar()
  val StartCooking = idc.addDispatchable()
  val DinnerReady = idc.addContingentVar()
  val v1 = idc.addVar()
  val v2 = idc.addVar()

  idc.enforceInterval(v1,StartDriving, 0, 10)
  idc.enforceInterval(WifeStore, v2, -5, 5)


  idc.addContingent(WifeStore, StartDriving, 30, 60)

  idc.addContingent(StartDriving, WifeHome, 35, 40)

  idc.addRequirement(WifeHome, DinnerReady, 5)

  idc.addRequirement(DinnerReady, WifeHome, 5)

//      idc.edg.exportToDot("before.dot", printer)

  idc.addContingent(StartCooking, DinnerReady, 25, 30)
  assert(idc.consistent)

//      idc.edg.exportToDot("incremental.dot", printer)
//      val full = idc.cc()
//      full.checkConsistencyFromScratch()
//      full.edg.exportToDot("full.dot", printer)

      // make sure the cooking starts at the right time
  assert(idc.hasRequirement(StartDriving, StartCooking, 10))
  assert(idc.hasRequirement(StartCooking, StartDriving, -10))

  idc.dispatch()
}