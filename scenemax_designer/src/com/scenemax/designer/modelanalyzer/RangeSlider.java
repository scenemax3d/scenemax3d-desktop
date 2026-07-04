package com.scenemax.designer.modelanalyzer;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class RangeSlider extends JComponent {
    private static final int HANDLE_SIZE = 14;
    private static final int TRACK_HEIGHT = 5;

    private final EventListenerList listenerList = new EventListenerList();
    private int minimum;
    private int maximum;
    private int lowerValue;
    private int upperValue;
    private int cursorValue;
    private DragHandle dragHandle = DragHandle.NONE;
    private DragHandle lastChangedHandle = DragHandle.NONE;

    RangeSlider(int minimum, int maximum) {
        this.minimum = minimum;
        this.maximum = Math.max(minimum, maximum);
        this.lowerValue = minimum;
        this.upperValue = this.maximum;
        this.cursorValue = minimum;
        setOpaque(false);
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                int lowerX = valueToX(lowerValue);
                int upperX = valueToX(upperValue);
                int cursorX = valueToX(cursorValue);
                int lowerDistance = Math.abs(e.getX() - lowerX);
                int upperDistance = Math.abs(e.getX() - upperX);
                int cursorDistance = Math.abs(e.getX() - cursorX);
                if (cursorDistance <= lowerDistance && cursorDistance <= upperDistance) {
                    dragHandle = DragHandle.CURSOR;
                } else if (lowerDistance <= upperDistance) {
                    dragHandle = DragHandle.LOWER;
                } else {
                    dragHandle = DragHandle.UPPER;
                }
                updateDraggedValue(e.getX());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                updateDraggedValue(e.getX());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragHandle = DragHandle.NONE;
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    int getMinimum() {
        return minimum;
    }

    void setMinimum(int minimum) {
        this.minimum = minimum;
        if (maximum < minimum) {
            maximum = minimum;
        }
        setRange(lowerValue, upperValue);
    }

    int getMaximum() {
        return maximum;
    }

    void setMaximum(int maximum) {
        this.maximum = Math.max(minimum, maximum);
        setRange(lowerValue, upperValue);
    }

    int getLowerValue() {
        return lowerValue;
    }

    int getUpperValue() {
        return upperValue;
    }

    int getCursorValue() {
        return cursorValue;
    }

    void setCursorValue(int cursorValue) {
        int nextCursor = clampToRange(cursorValue);
        boolean changed = nextCursor != this.cursorValue;
        this.cursorValue = nextCursor;
        repaint();
        if (changed) {
            lastChangedHandle = DragHandle.CURSOR;
            fireStateChanged();
        }
    }

    void setRange(int lower, int upper) {
        int nextLower = clamp(lower);
        int nextUpper = clamp(upper);
        if (nextUpper < nextLower) {
            nextUpper = nextLower;
        }
        int nextCursor = Math.max(nextLower, Math.min(nextUpper, cursorValue));
        boolean changed = nextLower != lowerValue || nextUpper != upperValue || nextCursor != cursorValue;
        lowerValue = nextLower;
        upperValue = nextUpper;
        cursorValue = nextCursor;
        repaint();
        if (changed) {
            if (dragHandle == DragHandle.LOWER || dragHandle == DragHandle.UPPER) {
                lastChangedHandle = dragHandle;
            } else {
                lastChangedHandle = DragHandle.NONE;
            }
            fireStateChanged();
        }
    }

    DragHandle getLastChangedHandle() {
        return lastChangedHandle;
    }

    boolean isCursorDragging() {
        return dragHandle == DragHandle.CURSOR;
    }

    void addChangeListener(ChangeListener listener) {
        listenerList.add(ChangeListener.class, listener);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(120, 30);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int y = getHeight() / 2;
        int left = trackLeft();
        int right = trackRight();
        int lowerX = valueToX(lowerValue);
        int upperX = valueToX(upperValue);
        int cursorX = valueToX(cursorValue);

        g2.setColor(new Color(77, 86, 99));
        g2.fillRoundRect(left, y - TRACK_HEIGHT / 2, Math.max(1, right - left), TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);

        g2.setColor(new Color(80, 145, 245));
        g2.fillRoundRect(lowerX, y - TRACK_HEIGHT / 2, Math.max(1, upperX - lowerX), TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);

        paintHandle(g2, lowerX, y, new Color(232, 238, 247));
        paintHandle(g2, upperX, y, new Color(232, 238, 247));
        paintCursor(g2, cursorX, y);
        g2.dispose();
    }

    private void paintHandle(Graphics2D g2, int x, int y, Color color) {
        int top = y - HANDLE_SIZE / 2;
        g2.setColor(new Color(17, 24, 34));
        g2.fillOval(x - HANDLE_SIZE / 2 - 1, top - 1, HANDLE_SIZE + 2, HANDLE_SIZE + 2);
        g2.setColor(color);
        g2.fillOval(x - HANDLE_SIZE / 2, top, HANDLE_SIZE, HANDLE_SIZE);
        g2.setColor(new Color(83, 96, 114));
        g2.drawOval(x - HANDLE_SIZE / 2, top, HANDLE_SIZE, HANDLE_SIZE);
    }

    private void paintCursor(Graphics2D g2, int x, int y) {
        int height = HANDLE_SIZE + 8;
        int top = y - height / 2;
        g2.setColor(new Color(17, 24, 34));
        g2.fillRoundRect(x - 3, top - 1, 7, height + 2, 5, 5);
        g2.setColor(new Color(255, 205, 92));
        g2.fillRoundRect(x - 2, top, 5, height, 5, 5);
    }

    private void updateDraggedValue(int x) {
        int value = xToValue(x);
        if (dragHandle == DragHandle.LOWER) {
            setRange(Math.min(value, upperValue), upperValue);
        } else if (dragHandle == DragHandle.UPPER) {
            setRange(lowerValue, Math.max(value, lowerValue));
        } else if (dragHandle == DragHandle.CURSOR) {
            setCursorValue(value);
        }
    }

    private int valueToX(int value) {
        int left = trackLeft();
        int width = Math.max(1, trackRight() - left);
        double ratio = maximum == minimum ? 0.0 : (clamp(value) - minimum) / (double) (maximum - minimum);
        return left + (int) Math.round(width * ratio);
    }

    private int xToValue(int x) {
        int left = trackLeft();
        int width = Math.max(1, trackRight() - left);
        double ratio = Math.max(0.0, Math.min(1.0, (x - left) / (double) width));
        return minimum + (int) Math.round((maximum - minimum) * ratio);
    }

    private int trackLeft() {
        return HANDLE_SIZE / 2 + 2;
    }

    private int trackRight() {
        return Math.max(trackLeft() + 1, getWidth() - HANDLE_SIZE / 2 - 2);
    }

    private int clamp(int value) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private int clampToRange(int value) {
        return Math.max(lowerValue, Math.min(upperValue, clamp(value)));
    }

    private void fireStateChanged() {
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener listener : listenerList.getListeners(ChangeListener.class)) {
            listener.stateChanged(event);
        }
    }

    enum DragHandle {
        NONE,
        LOWER,
        UPPER,
        CURSOR
    }
}
