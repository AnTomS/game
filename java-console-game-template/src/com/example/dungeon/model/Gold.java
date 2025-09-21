package com.example.dungeon.model;

public class Gold extends Item {
    private final int value;

    public Gold(String name, int value) {
        super(name);
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public void apply(GameState ctx) {
        System.out.println("Блестящее золото! Ценность: " + value + " монет.");
        ctx.addScore(value);
    }
}
