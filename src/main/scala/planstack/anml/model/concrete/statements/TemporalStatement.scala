package planstack.anml.model.concrete.statements

import planstack.anml.model._
import planstack.anml.model.abs.AbstractTemporalStatement
import planstack.anml.model.concrete.time.TemporalAnnotation
import planstack.anml.model.concrete.TemporalConstraint

/** Represents a temporally qualified, concrete ANML statement.
  *
  * @param interval Temporal interval in which the statement applies
  * @param statement An ANML statement on global variables
 */
class TemporalStatement(val interval:TemporalAnnotation, val statement:LogStatement) {

  def getTemporalConstraints : Seq[TemporalConstraint] = {
    val containerStart = interval.start.timepoint
    val containerEnd = interval.end.timepoint

    List(
      new TemporalConstraint(containerStart, "=", statement.start, interval.start.delta),
      new TemporalConstraint(containerEnd, "=", statement.end, interval.end.delta)
    )
  }

  override def toString = "%s %s".format(interval, statement)
}

object TemporalStatement {

  def apply(pb:AnmlProblem, context:Context, abs:AbstractTemporalStatement) =
    new TemporalStatement(TemporalAnnotation(pb, context, abs.annotation), abs.statement.bind(context))
}