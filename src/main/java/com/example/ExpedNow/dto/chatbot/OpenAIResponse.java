package com.example.ExpedNow.dto.chatbot;


import java.util.List;

public class OpenAIResponse {
    private List<Choice> choices;

    public String getAnswer() {
        return choices != null && !choices.isEmpty() ?
                choices.get(0).getMessage().getContent() :
                "لم أفهم سؤالك.";
    }

    // getter and setter for choices
    public List<Choice> getChoices() {
        return choices;
    }

    public void setChoices(List<Choice> choices) {
        this.choices = choices;
    }

    static class Choice {
        private Message message;

        // getter and setter
        public Message getMessage() {
            return message;
        }

        public void setMessage(Message message) {
            this.message = message;
        }
    }
}