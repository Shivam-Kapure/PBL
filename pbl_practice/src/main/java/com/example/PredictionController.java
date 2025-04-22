package com.example;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;

public class PredictionController {
    @FXML
    private VBox featureInputs;
    
    @FXML
    private Label predictionResult;
    
    private Map<String, TextField> featureFields = new HashMap<>();
    private List<String> featureNames;
    private Object scaler;
    private Object model;
    
    @FXML
    public void initialize() {
        try {
            // Load feature names
            String featureNamesJson = new String(Files.readAllBytes(Paths.get("output_plots/feature_names.json")));
            Type listType = new TypeToken<List<String>>(){}.getType();
            featureNames = new Gson().fromJson(featureNamesJson, listType);
            
            // Create input fields for each feature
            for (String feature : featureNames) {
                HBox inputBox = new HBox(5);
                Label label = new Label(feature + ":");
                TextField textField = new TextField();
                textField.setPromptText("Enter value");
                featureFields.put(feature, textField);
                
                inputBox.getChildren().addAll(label, textField);
                featureInputs.getChildren().add(inputBox);
            }
            
            // Load the scaler and model
            scaler = FileUtils.readFileToByteArray(new File("output_plots/scaler.joblib"));
            model = FileUtils.readFileToByteArray(new File("output_plots/best_model.joblib"));
            
        } catch (IOException e) {
            showError("Error loading prediction resources: " + e.getMessage());
        }
    }
    
    @FXML
    private void predict() {
        try {
            // Collect feature values
            Map<String, Double> featureValues = new HashMap<>();
            for (String feature : featureNames) {
                String value = featureFields.get(feature).getText();
                if (value.isEmpty()) {
                    showError("Please enter a value for " + feature);
                    return;
                }
                try {
                    featureValues.put(feature, Double.parseDouble(value));
                } catch (NumberFormatException e) {
                    showError("Invalid number format for " + feature);
                    return;
                }
            }
            
            // Convert features to JSON
            String featuresJson = new Gson().toJson(featureValues);
            
            // Get the path to the prediction script
            java.net.URL scriptUrl = getClass().getResource("/scripts/predict.py");
            if (scriptUrl == null) {
                throw new IOException("Could not find prediction script");
            }
            String scriptPath = new File(scriptUrl.toURI()).getAbsolutePath();
            
            // Run Python script
            ProcessBuilder pb = new ProcessBuilder("python", scriptPath, featuresJson);
            Process process = pb.start();
            
            // Read the output
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream())
            );
            String prediction = reader.readLine();
            
            int exitCode = process.waitFor();
            if (exitCode == 0 && prediction != null) {
                predictionResult.setText(String.format("Predicted Infant Mortality Rate: %.2f per 1000 live births", 
                    Double.parseDouble(prediction)));
            } else {
                showError("Error making prediction");
            }
            
        } catch (Exception e) {
            showError("Error making prediction: " + e.getMessage());
        }
    }
    
    @FXML
    private void goBack() throws IOException {
        App.setRoot("primary");
    }
    
    private void showError(String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
} 