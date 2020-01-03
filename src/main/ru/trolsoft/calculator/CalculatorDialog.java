/*
 * This file is part of trolCommander, http://www.trolsoft.ru/en/soft/trolcommander
 * Copyright (C) 2013-2016 Oleg Trifonov
 *
 * trolCommander is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * trolCommander is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ru.trolsoft.calculator;

import com.mucommander.cache.TextHistory;
import com.mucommander.ui.dialog.FocusDialog;
import com.mucommander.ui.helper.MnemonicHelper;
import com.mucommander.ui.layout.XAlignedComponentPanel;
import com.mucommander.ui.layout.XBoxPanel;
import com.mucommander.ui.layout.YBoxPanel;
import de.congrace.exp4j.CustomOperator;
import de.congrace.exp4j.ExpressionBuilder;
import org.jetbrains.annotations.NotNull;
import ru.trolsoft.utils.StrUtils;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.text.DecimalFormat;
import java.util.List;

/**
 * Created on 04/06/14.
 * @author Oleg Trifonov
 */
public class CalculatorDialog extends FocusDialog implements ActionListener, KeyListener {

    private static final Dimension MIN_DIMENSION = new Dimension(520, 300);

    private HistoryComboBox cbExpression;
    private JTextField edtDec, edtHex, edtBin, edtOct, edtExp;
    private JButton btnDec, btnHex, btnBin, btnOct, btnExp;
    private JButton btnClose;
    private JLabel lblError;

    private final CustomOperator OP_SHL = new CustomOperator("<<", true, 10, 2) {
        @Override
        protected double applyOperation(double[] values) {
            return Math.round(values[0]) << Math.round(values[1]);
        }
    };

    private final CustomOperator OP_SHR = new CustomOperator(">>", true, 11, 2) {
        @Override
        protected double applyOperation(double[] values) {
            return Math.round(values[0]) >> Math.round(values[1]);
        }
    };

    private final CustomOperator OP_AND = new CustomOperator("&", true, 8, 2) {
        @Override
        protected double applyOperation(double[] values) {
            return Math.round(values[0]) & Math.round(values[1]);
        }
    };

    private final CustomOperator OP_OR = new CustomOperator("|", true, 6, 2) {
        @Override
        protected double applyOperation(double[] values) {
            return Math.round(values[0]) | Math.round(values[1]);
        }
    };

    private final CustomOperator OP_NOT = new CustomOperator("~", true, 15, 1) {
        @Override
        protected double applyOperation(double[] values) {
            return ~Math.round(values[0]);
        }
    };

    private final CustomOperator OP_XOR = new CustomOperator("^^", true, 7, 2) {
        @Override
        protected double applyOperation(double[] values) {
            return Math.round(values[0]) ^ Math.round(values[1]);
        }
    };

    private final DecimalFormat FORMAT_DEC = new DecimalFormat("#.##################");
    private final DecimalFormat FORMAT_EXP = new DecimalFormat("0.00000000000000E0000");


    private final CustomOperator OPERATORS[] = {
        OP_SHL, OP_SHR, OP_AND, OP_OR, OP_NOT, OP_XOR
    };

    public CalculatorDialog(Frame owner) {
        super(owner, i18n("calculator.calculator"), null);

        Container contentPane = getContentPane();

        YBoxPanel yPanel = new YBoxPanel(10);

        // Text fields panel
        XAlignedComponentPanel compPanel = new XAlignedComponentPanel() {
            @Override
            public void add(@NotNull Component comp, Object constraints) {
                ((GridBagConstraints)constraints).fill = GridBagConstraints.HORIZONTAL;
                super.add(comp, constraints);
            }
        };

        List<String> calcHistory = TextHistory.getInstance().getList(TextHistory.Type.CALCULATOR);
        cbExpression = new HistoryComboBox(this, calcHistory);

        compPanel.addRow(i18n("calculator.expression")+":", cbExpression, 5);

        lblError = new JLabel();
        compPanel.addRow("", lblError, 10);
        //lblError.setVisible(false);

        Font buttonFont = Font.getFont(Font.MONOSPACED);

        btnDec = new JButton("DEC");
        btnDec.addActionListener(this);
        btnDec.setFont(buttonFont);
        this.edtDec = new JTextField();
        edtDec.setEditable(false);


        compPanel.addRow(btnDec, edtDec, 0);

        btnHex = new JButton("HEX");
        btnHex.addActionListener(this);
        btnHex.setFont(buttonFont);
        this.edtHex = new JTextField();
        edtHex.setEditable(false);
        compPanel.addRow(btnHex, edtHex, 0);

        btnBin = new JButton("BIN");
        btnBin.addActionListener(this);
        btnBin.setFont(buttonFont);
        this.edtBin = new JTextField();
        edtBin.setEditable(false);
        compPanel.addRow(btnBin, edtBin, 0);

        btnOct = new JButton("OCT");
        btnOct.addActionListener(this);
        btnOct.setFont(buttonFont);
        this.edtOct = new JTextField();
        edtOct.setEditable(false);
        compPanel.addRow(btnOct, edtOct, 0);

        btnExp = new JButton("EXP");
        btnExp.addActionListener(this);
        btnExp.setFont(buttonFont);
        this.edtExp = new JTextField();
        edtExp.setEditable(false);
        compPanel.addRow(btnExp, edtExp, 0);

        cbExpression.addActionListener(this);
        cbExpression.getEditor().getEditorComponent().addKeyListener(this);

        // Bottom line
        MnemonicHelper mnemonicHelper = new MnemonicHelper();

//        yPanel.add(new JLabel(dial = new SpinningDial()));
        XBoxPanel buttonsPanel = new XBoxPanel();
        JPanel buttonGroupPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        btnClose = new JButton(i18n("close"));
        btnClose.addActionListener(this);
        btnClose.setMnemonic(mnemonicHelper.getMnemonic(btnClose));
        buttonGroupPanel.add(btnClose);

        buttonsPanel.add(buttonGroupPanel);

        contentPane.add(buttonsPanel, BorderLayout.SOUTH);

        contentPane.add(yPanel, BorderLayout.NORTH);

        yPanel.add(compPanel);

        setMinimumSize(MIN_DIMENSION);
        setModal(false);

        fixHeight();
    }

    private boolean calculateAndShow() {
        String expression = getExpression();
        if (expression == null) {
            return false;
        }
        boolean success;
        try {
            double res = evaluate(expression);
            TextHistory.getInstance().add(TextHistory.Type.CALCULATOR, expression, false);
            cbExpression.addToHistory(expression);
            showResult(res);
            success = true;
        } catch (Exception e) {
            e.printStackTrace();
            clearResultFields();
            success = false;
        }
        enableControls(success);
        lblError.setText(success ? "" : i18n("calculator.error"));
        return success;
    }

    private void showResult(double res) {
        long valLong = Math.round(res);
        boolean isDecimal = valLong == res;
        edtDec.setText(isDecimal ? Long.toString(valLong) : FORMAT_DEC.format(res).replace(',', '.'));
        edtHex.setText(Long.toHexString(valLong));
        edtOct.setText(Long.toOctalString(valLong));
        edtBin.setText(Long.toBinaryString(valLong));
        edtExp.setText(formatExp(res));
    }

    private String getExpression() {
        Object selectedItem = cbExpression.getSelectedItem();
        if (selectedItem == null) {
            return null;
        }
        String result = selectedItem.toString().trim();
        return StrUtils.removeUtfMarker(result).trim();
    }


    private void enableControls(boolean enable) {
        edtDec.setEnabled(enable);
        edtHex.setEnabled(enable);
        edtOct.setEnabled(enable);
        edtBin.setEnabled(enable);
        edtOct.setEnabled(enable);
        edtExp.setEnabled(enable);
        btnDec.setEnabled(enable);
        btnHex.setEnabled(enable);
        btnOct.setEnabled(enable);
        btnBin.setEnabled(enable);
        btnOct.setEnabled(enable);
        btnExp.setEnabled(enable);
    }

    private void clearResultFields() {
        edtDec.setText("");
        edtHex.setText("");
        edtOct.setText("");
        edtBin.setText("");
        edtExp.setText("");
    }

    private double evaluate(String expression) throws Exception {
        if (expression.trim().isEmpty()) {
            return 0;
        }
        ExpressionBuilder builder = new ExpressionBuilder(expression);
        for (CustomOperator op : OPERATORS) {
            builder.withOperation(op);
        }
        builder.withVariable("pi", Math.PI);
        builder.withVariable("e", Math.E);
        return builder.build().calculate();
    }

    private String formatExp(double val) {
        String result = FORMAT_EXP.format(val).toUpperCase();
        int index = result.indexOf('E');
        if (index > 0 && result.charAt(index+1) != '-') {
            result = result.substring(0, index) + '+' + result.substring(index);
        }
        return result;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object src = e.getSource();
        if (src == cbExpression) {
            calculateAndShow();
        } else if (src == btnClose) {
            cancel();
        } else if (src == btnDec) {
            toClipboard(edtDec.getText());
        } else if (src == btnHex) {
            toClipboard(edtHex.getText());
        } else if (src == btnBin) {
            toClipboard(edtBin.getText());
        } else if (src == btnOct) {
            toClipboard(edtOct.getText());
        } else if (src == btnExp) {
            toClipboard(edtExp.getText());
        }
    }

    private void toClipboard(String s) {
        StringSelection data = new StringSelection(s);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(data, data);
    }


    @Override
    protected void saveState() {
        super.saveState();
        TextHistory.getInstance().save(TextHistory.Type.CALCULATOR);
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && (e.getModifiers() & (KeyEvent.CTRL_MASK | KeyEvent.META_MASK)) != 0) {
            if (calculateAndShow()) {
                cbExpression.setSelectedItem(edtDec.getText());
            }
        }
    }
}
