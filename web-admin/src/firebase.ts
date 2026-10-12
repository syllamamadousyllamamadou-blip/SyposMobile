import { initializeApp } from "firebase/app";
import { getFirestore } from "firebase/firestore";

const firebaseConfig = {
  apiKey: "AIzaSyAvjNMQJBPAHnl0MVm1_aHYqFqvfu0ugk8",
  projectId: "sypos-pro-e6745",
  storageBucket: "sypos-pro-e6745.firebasestorage.app",
  messagingSenderId: "651368642128",
  appId: "1:651368642128:web:abcdef1234567890" // Placeholder
};

export const app = initializeApp(firebaseConfig);
export const db = getFirestore(app);
