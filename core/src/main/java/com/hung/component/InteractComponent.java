package com.hung.component;

/**
 * The wish to use something nearby (open a chest). InputSystem sets it on the frame the interact
 * key is pressed (never while controls are off: free camera, hurt, dead); ChestSystem consumes it.
 */
public class InteractComponent {

    public boolean isRequested;
}
