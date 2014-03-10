package planstack.anml.model

import planstack.anml.parser

class AbstractTemporalStatement(val annotation:TemporalAnnotation, val statement:AbstractStatement) {

  override def toString = "%s %s".format(annotation, statement)
}


object AbstractTemporalStatement {

  def apply(pb:AnmlProblem, context:AbstractContext, ts:parser.TemporalStatement) : AbstractTemporalStatement = {
    new AbstractTemporalStatement(
      TemporalAnnotation(ts.annotation), AbstractStatement(pb, context, ts.statement)
    )
  }
}