/**
 * Copyright (c) 2016 All Rights Reserved by the SDL Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sdl.odata.jpa.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import java.util.List;

/**
 * Users entity.
 * @author Renze de Vries
 */
@Entity(name = "USERS")
public class User {
    private String userId;
    private String name;
    private String email;
    private int age;

    private List<PhotoItem> photos;

    public User(String userId, String name, String email, List<PhotoItem> photos) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.photos = photos;
    }

    public User() {
    }

    @Id
    @Column
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Column
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Column
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Column
    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    @OneToMany(mappedBy = "owner")
    public List<PhotoItem> getPhotos() {
        return photos;
    }

    public void setPhotos(List<PhotoItem> photos) {
        this.photos = photos;
    }
}
