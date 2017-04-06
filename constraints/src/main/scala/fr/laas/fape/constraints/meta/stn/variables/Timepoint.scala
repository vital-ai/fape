package fr.laas.fape.constraints.meta.stn.variables

import fr.laas.fape.constraints.meta.CSP
import fr.laas.fape.constraints.meta.constraints.Constraint
import fr.laas.fape.constraints.meta.domains.IntervalDomain
import fr.laas.fape.constraints.meta.stn.constraint.{AbsoluteAfterConstraint, AbsoluteBeforeConstraint, MinDelayConstraint}
import fr.laas.fape.constraints.meta.variables.{IVar, VarWithDomain}

class Timepoint(val id: Int, ref: Option[Any]) extends VarWithDomain {

  override def domain(implicit csp: CSP) : IntervalDomain = csp.dom(this)

  def isStructural : Boolean = false
  def isContingent : Boolean = false

  def <(tp: Timepoint) : MinDelayConstraint =
    new MinDelayConstraint(this, tp, 1)

  def <=(tp: Timepoint) : MinDelayConstraint =
    new MinDelayConstraint(this, tp, 0)

  def <=(deadline: Int) : AbsoluteBeforeConstraint = {
    new AbsoluteBeforeConstraint(this, deadline)
  }

  def <(deadline: Int) : AbsoluteBeforeConstraint = {
    new AbsoluteBeforeConstraint(this, deadline-1)
  }

  def >=(deadline: Int) : AbsoluteAfterConstraint = {
    new AbsoluteAfterConstraint(this, deadline)
  }

  def >(deadline: Int) : AbsoluteAfterConstraint = {
    new AbsoluteAfterConstraint(this, deadline+1)
  }

  override def ===(value: Int) = this <= value && this >= value

  override def =!=(value: Int): Constraint = this < value || this > value

  override def toString = ref match {
    case Some(x) => s"$x($id)"
    case None => s"tp$id"
  }

  override final def hashCode : Int = id
  override final def equals(o: Any) : Boolean = o match {
    case o: Timepoint => id == o.id
    case _ => false
  }

}
