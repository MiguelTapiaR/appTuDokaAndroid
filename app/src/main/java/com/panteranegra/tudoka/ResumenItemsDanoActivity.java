package com.panteranegra.tudoka;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.panteranegra.tudoka.Adapters.ItemsAdapter;
import com.panteranegra.tudoka.Model.Actividad;
import com.panteranegra.tudoka.Model.Auxiliares;
import com.panteranegra.tudoka.Model.MySingleton;
import com.panteranegra.tudoka.Model.Pieza;
import com.panteranegra.tudoka.Model.ReporteDano;
import com.panteranegra.tudoka.Model.ReporteDevolucion;
import com.panteranegra.tudoka.Model.ReporteSeguimiento;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ResumenItemsDanoActivity extends AppCompatActivity {

    ItemsAdapter adapter;

    private static final String TAG = "DocSnippets";
    Button continuarBtn;

    ListView contenido;
    ReporteDano reporte;
    private FirebaseAuth mAuth;
    String userId, nombreUser, emailUser, paisUser;
    String idReporteGenerado="";
    ProgressDialog progress;
    int flag=0;
    EditText numeroDevolucion;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resumen_items_dano);
        getUser();
        reporte = (ReporteDano) getIntent().getExtras().getSerializable("reporte");

        numeroDevolucion = (EditText) findViewById(R.id.et_no_devolucion_dano);
        progress= new ProgressDialog(this);

        progress.setTitle(getString(R.string.guardando));
        contenido = findViewById(R.id.list_view_items);
        adapter = new ItemsAdapter(getApplicationContext(),reporte.getAlPiezas());
        contenido.setAdapter(adapter);

        Button nuevoItemBTN = findViewById(R.id.button7);
        nuevoItemBTN.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), AgregarItemDanoActivity.class);
//                //para pasar el modelo
                intent.putExtra("reporte", reporte);
                startActivity(intent);
            }
        });

        continuarBtn = findViewById(R.id.button8);
        continuarBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(numeroDevolucion.getText().toString().matches("")){
                    Toast.makeText(getApplicationContext(),"Por favor ingresa un número de devolución", Toast.LENGTH_LONG);
                }else{
                    progress.show();
                    crearReporte();
                }

            }
        });
    }


    public void getUser(){
        mAuth = FirebaseAuth.getInstance();
        userId = mAuth.getCurrentUser().getUid();
        emailUser = mAuth.getCurrentUser().getEmail();

        //Init DB
        FirebaseFirestore db = FirebaseFirestore.getInstance();




        final DocumentReference docRef = db.collection("users").document(userId);
        docRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot snapshot,
                                @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    Log.w(TAG, "Listen failed.", e);
                    return;
                }

                String source = snapshot != null && snapshot.getMetadata().hasPendingWrites()
                        ? "Local" : "Server";

                if (snapshot != null && snapshot.exists()) {
                    Log.d(TAG, source + " data: " + snapshot.getData());
                    nombreUser = snapshot.getString("nombre");
                    paisUser = snapshot.getString("pais");
                } else {
                    Log.d(TAG, source + " data: null");
                }
            }
        });
    }

    public void crearReporte(){
        progress.setMessage("Guardando en Base de Datos");
        Long fecha = System.currentTimeMillis();

        Map<String, Object> docData = new HashMap<>();
        docData.put("cliente", reporte.getCliente().getKey());
        docData.put("fechaCreacion", fecha);
        docData.put("idUsuario", "keyUsuario");
        docData.put("pais", paisUser);
        docData.put("proyecto", reporte.getProyecto().getKey());

        // Get new Instance ID token

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("reportesDano")
                .add(docData)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        Log.d(TAG, "DocumentSnapshot added with ID: " + documentReference.getId());
                        idReporteGenerado = documentReference.getId();
                        cargaFotos();

                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error adding document", e);
                    }
                });

    }
    public Boolean cargaFotos(){
        for(Pieza actividad: reporte.getAlPiezas()){
            subirFotos(actividad);
        }
        return true;
    }

    public void subirFotos(final Pieza actividad){



        FirebaseStorage storage = FirebaseStorage.getInstance();

        StorageReference storageRef = storage.getReference();
        final String urlDescarga="";
        // Get the data from an ImageView as bytes

        Auxiliares auxiliares = new Auxiliares();
        Bitmap bm =auxiliares.getImageResized(getApplicationContext(), Uri.fromFile(new File(actividad.getFotoItemResumen())));

        Uri file = auxiliares.getImageUri(getApplicationContext(),bm);

        String[] aNombre= actividad.getFotoItemResumen().split("/");
        int indexNombre=(aNombre.length)-1;
        final StorageReference ref = storageRef.child("imagesSeguimientopb/"+aNombre[indexNombre]);
        UploadTask uploadTask = ref.putFile(file);

        Task<Uri> urlTask = uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
            @Override
            public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                if (!task.isSuccessful()) {
                    throw task.getException();
                }

                // Continue with the task to get the download URL

                return ref.getDownloadUrl();
            }
        }).addOnCompleteListener(new OnCompleteListener<Uri>() {
            @Override
            public void onComplete(@NonNull Task<Uri> task) {
                if (task.isSuccessful()) {
                    Uri downloadUri = task.getResult();
                    actividad.setUrl(downloadUri.toString());

                    actualizarBD(actividad);


                } else {
                    // Handle failures
                    // ...
                }
            }
        });

    }
    private void actualizarBD(Pieza actividad){
        Map<String, Object> docData = new HashMap<>();
        docData.put("descripcion", actividad.getDescripcion());
        docData.put("tipoDano", actividad.getTipoDano());
        docData.put("foto", actividad.getUrl());
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("reportesDano/"+idReporteGenerado+"/items/")
                .add(docData)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        Log.d(TAG, "DocumentSnapshot added with ID: " + documentReference.getId());

                        flag++;
                        if(flag==reporte.getAlPiezas().size()){
                            crearPDF();
                        }



                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error adding document", e);
                        Toast.makeText(getApplicationContext(),"Error", Toast.LENGTH_SHORT).show();

                    }
                });
    }


    public void crearPDF(){

        progress.setMessage(getString(R.string.generando_pdf));
        ArrayList<String> emails = new ArrayList<>();
        emails.add("rmontoya@themyt.com");
        HashMap<String, Object> map = new HashMap<>();// Mapeo previo
        Auxiliares aux = new Auxiliares();
        map.put("items", aux.convertirALItemstoJSON(reporte.getAlPiezas()));
        map.put("reporteId",idReporteGenerado );
        map.put("emails",emails );
        map.put("nombreCliente", reporte.getCliente().getNombre());
        map.put("numeroCliente", reporte.getCliente().getNumero());
        map.put("nombreProyecto", reporte.getProyecto().getNombre());
        map.put("numeroProyecto", reporte.getProyecto().getNumero());
        map.put("nombreUsuario", nombreUser);
        map.put("emailUsuario", emailUser);
        map.put("paisUsuario", paisUser);
        map.put("numeroDevolucion", numeroDevolucion.getText().toString());
        JSONObject jsonObject = new JSONObject(map);
        String url = "https://www.themyt.com/reportedoka/reporte_danoAndroid_v3_0.php";

        JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.POST,
                url, jsonObject,
                new Response.Listener<JSONObject>() {

                    int estado=0;
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            progress.dismiss();

                            estado= response.getInt("estado");

                            String respuesta= response.getString("respuesta");
                            String reporteId= response.getString("reporte_id");
                            String tipo= response.getString("tipoReporte");
                            Toast.makeText(getApplicationContext(),respuesta, Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(getApplicationContext(), EnviarMailsActivity.class);
                            //para pasar el modelo
                            intent.putExtra("urlReporte", "pdfs/"+reporteId+".pdf");
                            intent.putExtra("pais", paisUser);
                            intent.putExtra("email", emailUser);
                            intent.putExtra("nombre", nombreUser);
                            intent.putExtra("nombreProyecto", reporte.getProyecto().getNombre());
                            intent.putExtra("idReporte", reporteId);
                            intent.putExtra("tipo", tipo);
//                            intent.putExtra("urlReporte", "pdfs/"+idReporteGenerado+".pdf");
                            startActivity(intent);



                            //progressDialog.dismiss();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                    }
                }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                /*progressDialog.dismiss();
                toastPersonalizado.crearToast("Error de conexión, revisa tu conexión a internet e intenta nuevamente", null);*/
            }
        }) {

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<String, String>();
                headers.put("Content-Type", "application/json; charset=utf-8");
                headers.put("User-agent", "My useragent");
                return headers;
            }


        };
        jsonObjReq.setRetryPolicy(new DefaultRetryPolicy(60000,DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        MySingleton.getInstance(getApplicationContext()).addToRequestQueue(jsonObjReq);

    }
}