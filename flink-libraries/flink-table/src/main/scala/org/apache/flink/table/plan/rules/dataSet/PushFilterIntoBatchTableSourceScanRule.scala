/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.plan.rules.dataSet

import org.apache.calcite.plan.RelOptRule._
import org.apache.calcite.plan.{RelOptRule, RelOptRuleCall}
import org.apache.flink.table.plan.nodes.dataset.{BatchTableSourceScan, DataSetCalc}
import org.apache.flink.table.plan.rules.util.RexProgramExpressionExtractor._
import org.apache.flink.table.sources.FilterableTableSource

class PushFilterIntoBatchTableSourceScanRule extends RelOptRule(
  operand(classOf[DataSetCalc],
    operand(classOf[BatchTableSourceScan], none)),
  "PushFilterIntoBatchTableSourceScanRule") {

  override def matches(call: RelOptRuleCall) = {
    val scan: BatchTableSourceScan = call.rel(1).asInstanceOf[BatchTableSourceScan]
    scan.tableSource match {
      case _: FilterableTableSource => true
      case _ => false
    }
  }

  override def onMatch(call: RelOptRuleCall): Unit = {
    val calc: DataSetCalc = call.rel(0).asInstanceOf[DataSetCalc]
    val scan: BatchTableSourceScan = call.rel(1).asInstanceOf[BatchTableSourceScan]

    val tableSource = scan.tableSource.asInstanceOf[FilterableTableSource]

    val expression = extractExpression(calc.calcProgram)
    val unusedExpr = tableSource.setPredicate(expression)

    if (verifyExpressions(expression, unusedExpr)) {

      val newCalcProgram = rewriteRexProgram(
        calc.calcProgram,
        scan,
        unusedExpr,
        scan.tableSource)(call.builder())

      val newCalc = new DataSetCalc(
        calc.getCluster,
        calc.getTraitSet,
        scan,
        calc.getRowType,
        newCalcProgram,
        description)

      call.transformTo(newCalc)
    }
  }
}

object PushFilterIntoBatchTableSourceScanRule {
  val INSTANCE: RelOptRule = new PushFilterIntoBatchTableSourceScanRule
}
