// MainActivity.kt
package com.example.miruta

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import java.util.Locale
import android.content.Context
import android.content.Intent
import android.location.Address
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup

data class Direccion(val texto: String)

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnAdd: Button
    private lateinit var btnOpenScanner: Button
    private lateinit var btnOrdenar: Button
    private lateinit var btnOrdenarFormato: Button
    private lateinit var tvDetectedText: TextView
    private lateinit var etDireccionManual: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DireccionAdapter

    private val listaDirecciones = mutableListOf<Direccion>()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var isScannerActive = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startCamera()
        else Log.e("PERMISO", "Permiso de cámara denegado")
    }

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Log.e("PERMISO", "Permiso de ubicación denegado")
            Toast.makeText(this, "Permiso de ubicación es necesario para ordenar por distancia", Toast.LENGTH_SHORT).show()
        }
    }

    class DireccionAdapter(private val direcciones: MutableList<Direccion>) :
        RecyclerView.Adapter<DireccionAdapter.DireccionViewHolder>() {

        inner class DireccionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvDireccion: TextView = itemView.findViewById(R.id.tvDireccion)
            val btnGoogleMaps: Button = itemView.findViewById(R.id.btnGoogleMaps)
            val btnWaze: Button = itemView.findViewById(R.id.btnWaze)
            val btnEliminar: Button = itemView.findViewById(R.id.btnBorrar)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DireccionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_direccion, parent, false)
            return DireccionViewHolder(view)
        }

        override fun onBindViewHolder(holder: DireccionViewHolder, position: Int) {
            val direccion = direcciones[position]
            holder.tvDireccion.text = direccion.texto

            holder.btnGoogleMaps.setOnClickListener {
                val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(direccion.texto)}")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.setPackage("com.google.android.apps.maps")
                it.context.startActivity(intent)
            }

            holder.btnWaze.setOnClickListener {
                val uri = Uri.parse("https://waze.com/ul?q=${Uri.encode(direccion.texto)}")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.setPackage("com.waze")
                it.context.startActivity(intent)
            }

            holder.btnEliminar.setOnClickListener {
                direcciones.removeAt(position)
                notifyItemRemoved(position)
                notifyItemRangeChanged(position, direcciones.size)
            }
        }

        override fun getItemCount(): Int = direcciones.size

        fun agregarDireccion(direccion: Direccion) {
            direcciones.add(direccion)
            notifyItemInserted(direcciones.size - 1)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        btnAdd = findViewById(R.id.btnAdd)
        btnOpenScanner = findViewById(R.id.btnOpenScanner)
        btnOrdenar = findViewById(R.id.btnOrdenar)
        btnOrdenarFormato = findViewById(R.id.btnOrdenarFormato)
        tvDetectedText = findViewById(R.id.tvDetectedText)
        etDireccionManual = findViewById(R.id.etDireccionManual)
        recyclerView = findViewById(R.id.recyclerView)

        previewView.visibility = View.GONE

        adapter = DireccionAdapter(listaDirecciones)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        var ascendente = true

        btnOrdenar.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                Toast.makeText(this, "Por favor, otorgue permiso de ubicación y vuelva a ordenar.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            obtenerUbicacionActual { latActual, lonActual ->
                val direccionesConCoordenadas = mutableListOf<Triple<Direccion, Double, Double>?>()
                var pendientes = listaDirecciones.size
                if (pendientes == 0) return@obtenerUbicacionActual

                listaDirecciones.forEach { direccion ->
                    geocodificarDireccion(direccion.texto) { coords ->
                        if (coords != null) {
                            direccionesConCoordenadas.add(Triple(direccion, coords.first, coords.second))
                        } else {
                            direccionesConCoordenadas.add(null)
                        }

                        pendientes--
                        if (pendientes == 0) {
                            val ordenadas = direccionesConCoordenadas.mapNotNull { it }.let {
                                if (ascendente)
                                    it.sortedBy { calcularDistancia(latActual, lonActual, it.second, it.third) }
                                else
                                    it.sortedByDescending { calcularDistancia(latActual, lonActual, it.second, it.third) }
                            }

                            listaDirecciones.clear()
                            listaDirecciones.addAll(ordenadas.map { it.first })
                            btnOrdenar.text = if (ascendente) "Ordenar descendente" else "Ordenar ascendente"
                            ascendente = !ascendente
                            adapter.notifyDataSetChanged()
                        }
                    }
                }
            }
        }

        btnOrdenarFormato.setOnClickListener {
            listaDirecciones.sortWith(comparadorDirecciones)
            adapter.notifyDataSetChanged()
            Toast.makeText(this, "Direcciones ordenadas por formato", Toast.LENGTH_SHORT).show()
        }

        btnOpenScanner.setOnClickListener {
            if (!isScannerActive) {
                previewView.visibility = View.VISIBLE
                isScannerActive = true

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    startCamera()
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                }

                btnOpenScanner.text = "Cerrar Escáner"
            } else {
                stopCamera()
                btnOpenScanner.text = "Abrir Escáner"
            }
        }

        btnAdd.setOnClickListener {
            val direccionManual = etDireccionManual.text.toString().trim()
            val direccionDetectada = tvDetectedText.text.toString().trim()
            val direccionFinal = when {
                direccionManual.isNotEmpty() -> direccionManual
                direccionDetectada.isNotEmpty() -> direccionDetectada
                else -> null
            }

            if (direccionFinal != null) {
                adapter.agregarDireccion(Direccion(direccionFinal))
                etDireccionManual.text.clear()
                tvDetectedText.text = ""
                stopCamera()
                btnOpenScanner.text = "Abrir Escáner"
                Toast.makeText(this, "Dirección agregada", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No hay dirección que agregar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val comparadorDirecciones = Comparator<Direccion> { d1, d2 ->
        val regex = Regex("""(?i)\b(?:cll|cra|tv|diag|av|calle|carrera|transversal|diagonal|avenida)[^\d]*(\d+)[^\d#]*(?:#\s*(\d+))?[-\s]*(\d+)?""")

        fun normalizarTipo(tipo: String?): Int {
            return when (tipo?.lowercase()) {
                "calle", "cll" -> 1
                "carrera", "cra" -> 2
                "diagonal", "diag" -> 3
                "transversal", "tv" -> 4
                "avenida", "av" -> 5
                else -> 6
            }
        }

        fun extraerComponentes(direccion: String): Quadruple<Int, Int, Int, Int> {
            val match = regex.find(direccion)
            val tipoTexto = match?.value?.split(" ")?.firstOrNull()
            val tipo = normalizarTipo(tipoTexto)
            val num1 = match?.groups?.get(1)?.value?.toIntOrNull() ?: Int.MAX_VALUE
            val num2 = match?.groups?.get(2)?.value?.toIntOrNull() ?: Int.MAX_VALUE
            val num3 = match?.groups?.get(3)?.value?.toIntOrNull() ?: Int.MAX_VALUE
            return Quadruple(tipo, num1, num2, num3)
        }

        val a = extraerComponentes(d1.texto)
        val b = extraerComponentes(d2.texto)

        when {
            a.first != b.first -> return@Comparator a.first - b.first
            a.second != b.second -> return@Comparator a.second - b.second
            a.third != b.third -> return@Comparator a.third - b.third
            else -> return@Comparator a.fourth - b.fourth
        }
    }


    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(imageProxy)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (exc: Exception) {
                Log.e("CAMARA", "Error al iniciar cámara", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        isScannerActive = false
        previewView.visibility = View.GONE
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val direccionPattern = Regex(
                        """^(?i)(Calle|Cra|Carrera|Tv|Transversal|Diag|Diagonal|Av|Avenida|Cll|Circular|Cir)\s*\d+[A-Za-z]?\s*(#|No\.?)\s*\d+[A-Za-z]?\s*-\s*\d+[A-Za-z]?$""",
                        RegexOption.IGNORE_CASE
                    )

                    val lineas = visionText.text.lines()
                    for (linea in lineas) {
                        val textoLimpio = linea.trim()
                        if (direccionPattern.matches(textoLimpio)) {
                            val yaExiste = listaDirecciones.any { it.texto.equals(textoLimpio, ignoreCase = true) }
                            if (!yaExiste) {
                                runOnUiThread {
                                    tvDetectedText.text = textoLimpio
                                    adapter.agregarDireccion(Direccion(textoLimpio))
                                    Toast.makeText(this, "Dirección agregada automáticamente", Toast.LENGTH_SHORT).show()
                                }
                            }
                            break // Solo capturar una dirección por frame
                        }
                    }


                    val match = direccionPattern.find(visionText.text)
                    if (match != null) {
                        val direccionEncontrada = match.value.trim()

                        val yaExiste = listaDirecciones.any { it.texto.equals(direccionEncontrada, ignoreCase = true) }

                        if (!yaExiste) {
                            runOnUiThread {
                                tvDetectedText.text = direccionEncontrada
                                adapter.agregarDireccion(Direccion(direccionEncontrada))
                                Toast.makeText(this, "Dirección agregada automáticamente", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        runOnUiThread {
                            tvDetectedText.text = ""
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("OCR", "Error al procesar imagen", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }



    private fun geocodificarDireccion(direccion: String, callback: (Pair<Double, Double>?) -> Unit) {
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            geocoder.getFromLocationName(direccion, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (addresses.isNotEmpty()) {
                        val location = addresses[0]
                        callback(Pair(location.latitude, location.longitude))
                    } else {
                        callback(null)
                    }
                }

                override fun onError(errorMessage: String?) {
                    Log.e("GEOCODER", "Error geocodificando dirección: $errorMessage")
                    callback(null)
                }
            })
        } catch (e: Exception) {
            Log.e("GEOCODER", "Excepción al geocodificar", e)
            callback(null)
        }
    }

    private fun calcularDistancia(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val resultados = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, resultados)
        return resultados[0].toDouble()
    }

    private fun obtenerUbicacionActual(callback: (Double, Double) -> Unit) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                callback(location.latitude, location.longitude)
            } else {
                Toast.makeText(this, "No se pudo obtener ubicación actual", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error obteniendo ubicación: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
