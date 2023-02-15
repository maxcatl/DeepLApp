package com.maxcatl.deepl;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Logger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    /**
     * Les différents attributs de l'app
     */
    private EditText editTextOri;
    private TextView textViewTra;
    //TODO: remettre clé situé dans le fichier key
    private String url = "https://api-free.deepl.com/v2/translate?auth_key=";
    private String langOri;
    private String langTra;

    /**
     * Méthode appelée lors de la création de l'activité (ici au lancement de l'app)
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //défini l'activité
        setContentView(R.layout.activity_main);
        //défini la barre d'action
        setSupportActionBar(findViewById(R.id.appBar));


        //Retrouve tous les champs et boutons de l'app
        Button btnTraduire = findViewById(R.id.buttonTranslate);
        Button btnCancel = findViewById(R.id.buttonCancel);
        editTextOri = findViewById(R.id.editTextOriginal);
        textViewTra = findViewById(R.id.textTranslated);
        Spinner spinnerOri = findViewById(R.id.spinnerOriginal);
        Spinner spinnerTra = findViewById(R.id.spinnerTranslate);
        langOri = "none";
        langTra = "fr";

        //sélectionne le français comme langue cible par défaut
        //S'il n'est pas trouvé, la première langue de la liste sera sélectionnée par défaut
        int i = 0;
        boolean francais = false;
        while(!francais && i<spinnerTra.getCount())
        {
            if (spinnerTra.getItemAtPosition(i).equals("Français"))
            {
                spinnerTra.setSelection(i, false);
                francais = true;
            }
            i++;
        }

        //On retrouve la langue lorsque la langue par défaut est sélectionnée
        spinnerOri.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Logger.getGlobal().severe(getResources().getStringArray(R.array.langues_originales_abbr)[position]);
                langOri = getResources().getStringArray(R.array.langues_originales_abbr)[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                //on ne change rien si rien n'est sélectionné
            }
        });

        //On retrouve la langue lorsque la langue cible est sélectionnée
        spinnerTra.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Logger.getGlobal().info(getResources().getStringArray(R.array.langues_traduites_abbr)[position]);
                langTra = getResources().getStringArray(R.array.langues_traduites_abbr)[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                //on ne change rien si rien n'est sélectionné
            }
        });

        //On lance la traduction lorsque le bouton traduire est pressé
        btnTraduire.setOnClickListener(v -> {
            run();

            // Remise à 0 de l'url
            //TODO: remettre clé situé dans le fichier key
            url = "https://api-free.deepl.com/v2/translate?auth_key=";
        });

        //on remet le champ de saisie à 0 lorsque le bouton "annuler" est pressé
        btnCancel.setOnClickListener(v -> editTextOri.setText(""));

    }


    /**
     * Méthode permettant de traiter les infos avant d'envoyer à la requête
     */
    public void run()
    {
        //on récupère le texte à traduire
        String text = editTextOri.getText().toString();
        //S'il est vide, on ne fait rien
        if (text.equals(""))
        {
            return;
        }

        //Prépare le texte à traduire pour être mis dans le lien
        text = text.replace(" ", "%20");
        text = text.replace("\n", "%0A");

        //ajoute le texte et la langue de traduction dans l'url
        url += "&text=" + text;
        url += "&target_lang=" + langTra;

        //ajoute la langue d'origine dans l'url si elle est spécifiée
        if (!langOri.equals("none"))
        {
            url += "&source_lang=" + langOri;
        }

        launchRequest();

    }

    /**
     * Méthode permettant de lancer la requête à l'API DeepL
     */
    public void launchRequest() {
        //création du client http et de la requête
        OkHttpClient client = new OkHttpClient();
        Logger.getGlobal().info(url);
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            /**
             * Si la requête http n'a pas abouti
             * @param call le callback
             * @param e l'erreur
             */
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                call.cancel();
            }

            /**
             * lorsque la requête a abouti
             * @param call le callback
             * @param response la réponse
             */
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response){
                final String myResponse;
                try {
                    myResponse = Objects.requireNonNull(response.body()).string();
                    processResponse(myResponse);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });
    }

    /**
     * Méthode permetttant de traiter la réponse de l'API DeepL lorsque la requête a abouti
     * @param response la réponse de l'API
     */
    public void processResponse(String response) {
        //on crée un autre thread pour chercher la traduction et la mettre dans le text view
        MainActivity.this.runOnUiThread(() -> {
            try {
                JSONObject reponseJSON = new JSONObject(response);
                JSONArray tableauJSON = reponseJSON.optJSONArray("translations");
                if (tableauJSON == null)
                {
                    throw new NoTranslationException("NoTranslation : " + response);
                }

                //récupération de la traduction dans le JSON
                String traduction = tableauJSON.optJSONObject(0).optString("text");
                if (traduction.equals(""))
                {
                    throw new NoTranslationException("No translation returned  by the API");
                }

                textViewTra.setText(traduction);
            }
            catch (NoTranslationException e)
            {
                Logger.getGlobal().severe(e.getMessage());
                Logger.getGlobal().severe(url);
            }
            catch (JSONException e)
            {
                Logger.getGlobal().severe("error on the JSON");
                e.printStackTrace();
            }
        });
    }

    /**
     * Classe héritant d'Exception symbolisant une exception lorsque aucune traduction n'a été trouvée dans la réponse de l'API
     */
    private static class NoTranslationException extends Exception
    {
        private final String message;

        /**
         * Constructeur requérant un message
         * @param message un descriptif de l'erreur
         */
        public NoTranslationException(String message)
        {
            this.message = message;
        }

        /**
         * Méthode permettant d'obtenir le message lié à l'exception
         * @return le messeg lié à l'exception
         */
        @Override
        public String getMessage()
        {
            return "NoTranslationException: " + message;
        }
    }
}