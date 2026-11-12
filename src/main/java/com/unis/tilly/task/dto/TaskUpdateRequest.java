package com.unis.tilly.task.dto;

import com.fasterxml.jackson.annotation.JsonSetter;

// 5.4: 보낸 필드만 바꾼다. 필드를 생략하면 유지, date/dueDate를 명시적 null로 보내면 해제.
// record로는 "생략"과 "null"을 구분할 수 없어서 setter 호출 여부로 구분한다.
public class TaskUpdateRequest {

    private String title;
    private Boolean completed;
    private String date;
    private boolean dateSent;
    private String dueDate;
    private boolean dueDateSent;

    public String title() {
        return title;
    }

    public Boolean completed() {
        return completed;
    }

    public String date() {
        return date;
    }

    public boolean dateSent() {
        return dateSent;
    }

    public String dueDate() {
        return dueDate;
    }

    public boolean dueDateSent() {
        return dueDateSent;
    }

    @JsonSetter("title")
    public void setTitle(String title) {
        this.title = title;
    }

    @JsonSetter("completed")
    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }

    @JsonSetter("date")
    public void setDate(String date) {
        this.date = date;
        this.dateSent = true;
    }

    @JsonSetter("dueDate")
    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
        this.dueDateSent = true;
    }
}
