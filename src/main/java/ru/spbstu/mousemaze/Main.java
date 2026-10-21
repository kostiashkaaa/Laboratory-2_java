package ru.spbstu.mousemaze;

import ru.spbstu.mousemaze.ui.MainFrame;

import javax.swing.*;


public final class Main {
    private Main() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}