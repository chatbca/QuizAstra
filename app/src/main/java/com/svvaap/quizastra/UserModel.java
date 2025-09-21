package com.svvaap.quizastra;


public class UserModel {
    private String name;
    private int rank;

    public UserModel(String name, int rank) {
        this.name = name;
        this.rank = rank;
    }

    public String getName() {
        return name;
    }

    public int getRank() {
        return rank;
    }
}
