/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.gates;

import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.hdlgenerator.AbstractHDLGeneratorFactory;
import com.cburch.logisim.fpga.hdlgenerator.HDL;
import com.cburch.logisim.fpga.hdlgenerator.HDLParameters;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.LineBuffer;
import java.util.ArrayList;

public class AbstractGateHDLGenerator extends AbstractHDLGeneratorFactory {

  private static final int BIT_WIDTH_GENERIC = -1;
  private static final String BIT_WIDTH_STRING = "NrOfBits";
  private static final int BUBBLES_GENERIC = -2;
  private static final String BUBBLES_MASK = "BubblesMask";

  public AbstractGateHDLGenerator() {
    super();
    myParametersList
        .addBusOnly(BIT_WIDTH_STRING, BIT_WIDTH_GENERIC)
        .addVector(BUBBLES_MASK, BUBBLES_GENERIC, HDLParameters.MAP_GATE_INPUT_BUBLE);
    getWiresPortsduringHDLWriting = true;
  }

  @Override
  public void getGenerationTimeWiresPorts(Netlist theNetlist, AttributeSet attrs) {
    if (!attrs.containsAttribute(GateAttributes.ATTR_INPUTS)) return;
    final var nrOfInputs = attrs.getValue(GateAttributes.ATTR_INPUTS);
    final var bitWidth = attrs.getValue(StdAttr.WIDTH).getWidth();
    for (var input = 1; input <= nrOfInputs; input++) {
      myWires.addWire(String.format("s_real_input_%d", input), bitWidth == 1 ? 1 : BIT_WIDTH_GENERIC);
      final var floatingToZero = getFloatingValue(attrs.getValue(new NegateAttribute(input - 1, null)));
      myPorts.add(Port.INPUT, String.format("Input_%d", input), BIT_WIDTH_GENERIC, input, floatingToZero);
    }
    myPorts.add(Port.OUTPUT, "Result", BIT_WIDTH_GENERIC, 0, StdAttr.WIDTH);
  }

  public boolean getFloatingValue(boolean isInverted) {
    return !isInverted;
  }

  public ArrayList<String> getLogicFunction(int nrOfInputs, int bitwidth, boolean isOneHot) {
    return new ArrayList<>();
  }

  @Override
  public ArrayList<String> GetModuleFunctionality(Netlist nets, AttributeSet attrs) {
    final var contents = LineBuffer.getHdlBuffer();
    final var bitWidth = attrs.getValue(StdAttr.WIDTH).getWidth();
    final var nrOfInputs =
        attrs.containsAttribute(GateAttributes.ATTR_INPUTS)
            ? attrs.getValue(GateAttributes.ATTR_INPUTS)
            : 1;

    if (nrOfInputs > 1) {
      contents.empty();
      contents.addRemarkBlock("Here the bubbles are processed");
      for (var i = 0; i < nrOfInputs; i++) {
        if (HDL.isVHDL()) {
          contents.add("  s_real_input_{{1}} {{=}} Input_{{1}} WHEN {{2}}{{<}}{{3}}{{>}} = '0' ELSE NOT(Input_{{1}});", (i + 1), BUBBLES_MASK, i);
        } else {
          contents.add("  {{assign}} s_real_input_{{1}} {{=}} ({{2}}{{<}}{{3}}{{>}} == 1'b0) ? Input_{{1}} : ~Input_{{1}};", (i + 1), BUBBLES_MASK, i);
        }
      }
    }
    contents.empty().addRemarkBlock("Here the functionality is defined");
    var onehot = false;
    if (attrs.containsAttribute(GateAttributes.ATTR_XOR)) {
      onehot = attrs.getValue(GateAttributes.ATTR_XOR) == GateAttributes.XOR_ONE;
    }
    contents.add(getLogicFunction(nrOfInputs, bitWidth, onehot));
    return contents.get();
  }

  public ArrayList<String> GetOneHot(boolean inverted, int nrOfInputs, boolean isBus) {
    var lines = new ArrayList<String>();
    var spaces = "   ";
    var indexString = "";
    if (isBus) {
      if (HDL.isVHDL()) {
        lines.add(spaces + "GenBits : FOR n IN (" + BIT_WIDTH_STRING + "-1) DOWNTO 0 GENERATE");
        spaces += "   ";
        indexString = "(n)";
      } else {
        lines.add("   genvar n;");
        lines.add("   generate");
        lines.add("      for (n = 0 ; n < " + BIT_WIDTH_STRING + " ; n = n + 1)");
        lines.add("         begin: bit");
        spaces += "         ";
        indexString = "[n]";
      }
    }
    var oneLine = new StringBuilder();
    oneLine
        .append(spaces)
        .append(HDL.assignPreamble())
        .append("Result")
        .append(indexString)
        .append(HDL.assignOperator());
    if (inverted) oneLine.append(HDL.notOperator()).append("(");
    final var spacesLen = oneLine.length();
    for (var termloop = 0; termloop < nrOfInputs; termloop++) {
      while (oneLine.length() < spacesLen) {
        oneLine.append(" ");
      }
      oneLine.append("(");
      for (var i = 0; i < nrOfInputs; i++) {
        if (i == termloop) {
          oneLine.append("s_real_input_").append(i + 1).append(indexString);
        } else {
          oneLine.append(HDL.notOperator()).append("(s_real_input_").append(i + 1).append(indexString).append(")");
        }
        if (i < (nrOfInputs - 1)) {
          oneLine.append(" ").append(HDL.andOperator()).append(" ");
        }
      }
      oneLine.append(")");
      if (termloop < (nrOfInputs - 1)) {
        oneLine.append(" ").append(HDL.orOperator()).append(" ");
      } else {
        if (inverted) oneLine.append(")");
        oneLine.append(";");
      }
      lines.add(oneLine.toString());
      oneLine.setLength(0);
    }
    if (isBus) {
      if (HDL.isVHDL()) {
        lines.add("   END GENERATE GenBits;");
      } else {
        lines.add("         end");
        lines.add("   endgenerate");
      }
    }
    return lines;
  }

  public ArrayList<String> GetParity(boolean inverted, int nrOfInputs, boolean isBus) {
    final var lines = new ArrayList<String>();
    var spaces = "   ";
    var indexString = "";
    if (isBus) {
      if (HDL.isVHDL()) {
        lines.add(spaces + "GenBits : FOR n IN (" + BIT_WIDTH_STRING + "-1) DOWNTO 0 GENERATE");
        spaces += "   ";
        indexString = "(n)";
      } else {
        lines.add("   genvar n;");
        lines.add("   generate");
        lines.add("      for (n = 0 ; n < " + BIT_WIDTH_STRING + " ; n = n + 1)");
        lines.add("         begin: bit");
        spaces += "         ";
        indexString = "[n]";
      }
    }
    final var oneLine = new StringBuilder();
    oneLine.append(spaces).append(HDL.assignPreamble()).append("Result").append(indexString).append(HDL.assignOperator());
    if (inverted) oneLine.append(HDL.notOperator()).append("(");
    final var spacesLen = oneLine.length();
    for (var i = 0; i < nrOfInputs; i++) {
      while (oneLine.length() < spacesLen) {
        oneLine.append(" ");
      }
      oneLine.append("s_real_input_").append(i + 1).append(indexString);
      if (i < (nrOfInputs - 1)) {
        oneLine.append(HDL.xorOperator());
      } else {
        if (inverted) oneLine.append(")");
        oneLine.append(";");
      }
      lines.add(oneLine.toString());
      oneLine.setLength(0);
    }
    if (isBus) {
      if (HDL.isVHDL()) {
        lines.add("   END GENERATE GenBits;");
      } else {
        lines.add("         end");
        lines.add("   endgenerate");
      }
    }
    return lines;
  }

  @Override
  public boolean isHDLSupportedTarget(AttributeSet attrs) {
    var supported = true;
    if (attrs.containsAttribute(GateAttributes.ATTR_OUTPUT))
      supported = attrs.getValue(GateAttributes.ATTR_OUTPUT).equals(GateAttributes.OUTPUT_01);
    return supported;
  }
}
