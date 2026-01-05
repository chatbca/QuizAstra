package com.example.quizastra;

public class User {
    private String id;
    private String name;
    private String email;
    private String phone;
    private Integer avatarResId; // optional
    private String photoBase64; // optional Base64 avatar
    private String photoUri;    // optional URI avatar

    // Required empty constructor for Firebase
    public User() {}

    public User(String id, String name, String email, String phone, Integer avatarResId) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.avatarResId = avatarResId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Integer getAvatarResId() { return avatarResId; }
    public void setAvatarResId(Integer avatarResId) { this.avatarResId = avatarResId; }

    public String getPhotoBase64() { return photoBase64; }
    public void setPhotoBase64(String photoBase64) { this.photoBase64 = photoBase64; }

    public String getPhotoUri() { return photoUri; }
    public void setPhotoUri(String photoUri) { this.photoUri = photoUri; }
}
