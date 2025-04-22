import sys
import json
import joblib
import numpy as np

def predict(features_json):
    # Load the model and scaler
    scaler = joblib.load('output_plots/scaler.joblib')
    model = joblib.load('output_plots/best_model.joblib')
    
    # Convert features to numpy array
    features = np.array(list(json.loads(features_json).values())).reshape(1, -1)
    
    # Scale features and make prediction
    scaled_features = scaler.transform(features)
    prediction = model.predict(scaled_features)[0]
    
    print(prediction)

if __name__ == "__main__":
    if len(sys.argv) > 1:
        predict(sys.argv[1]) 