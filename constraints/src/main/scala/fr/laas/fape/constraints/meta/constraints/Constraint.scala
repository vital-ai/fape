package fr.laas.fape.constraints.meta.constraints


import fr.laas.fape.constraints.bindings.InconsistentBindingConstraintNetwork
import fr.laas.fape.constraints.meta.CSP
import fr.laas.fape.constraints.meta.events.Event
import fr.laas.fape.constraints.meta.variables.{IVar, IntVariable}

trait Constraint {

  type Satisfaction = ConstraintSatisfaction.ConstraintSatisfaction

  def variables(implicit csp: CSP): Set[IVar]

  def subconstraints(implicit csp: CSP) : Iterable[Constraint] = Nil

  def onPost(implicit csp: CSP) {
    for(c <- subconstraints)
      csp.watchSubConstraint(c, this)
  }

  def onWatch(implicit csp: CSP) {
    for(c <- subconstraints)
      csp.watchSubConstraint(c, this)
  }

  final def propagate(event: Event)(implicit csp: CSP): Unit = {
    csp.log.startConstraintPropagation(this)
    _propagate(event)
    if(isSatisfied)
      csp.setSatisfied(this)
    if(isViolated)
      throw new InconsistentBindingConstraintNetwork()
    csp.log.endConstraintPropagation(this)
  }

  protected def _propagate(event: Event)(implicit csp: CSP)

  def satisfaction(implicit csp: CSP) : Satisfaction

  /** Returns the invert of this constraint (e.g. === for an =!= constraint) */
  def reverse: Constraint

  final def isSatisfied(implicit csp: CSP) = satisfaction == ConstraintSatisfaction.SATISFIED

  final def isViolated(implicit csp: CSP) = satisfaction == ConstraintSatisfaction.VIOLATED

  final def active(implicit csp: CSP) : Boolean = csp.constraints.isActive(this)
  final def watched(implicit csp: CSP) : Boolean = csp.constraints.isWatched(this)

  def &&(constraint: Constraint) : ConjunctionConstraint = constraint match {
    case c: ConjunctionConstraint => new ConjunctionConstraint(constraint :: c.constraints.toList)
    case c => new ConjunctionConstraint(List(this, c))
  }

  def ||(constraint: Constraint) = constraint match {
    case c: DisjunctiveConstraint => new DisjunctiveConstraint(constraint :: c.disjuncts.toList)
    case c => new DisjunctiveConstraint(List(this, c))
  }
}


object ConstraintSatisfaction extends Enumeration {
  type ConstraintSatisfaction = Value
  val SATISFIED, VIOLATED, UNDEFINED = Value
}