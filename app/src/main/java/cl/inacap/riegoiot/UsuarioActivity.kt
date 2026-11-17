package cl.inacap.riegoiot

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cl.inacap.riegoiot.databinding.ActivityUsuarioBinding
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class UsuarioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUsuarioBinding
    private val db = FirebaseFirestore.getInstance()

    /** Doc de la solicitud en curso: se reutiliza en cada reintento para no duplicar. */
    private var ref: DocumentReference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsuarioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Sesion.exigirRol(this, Sesion.USUARIO)

        binding.toolbarUsuario.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_logout) {
                Sesion.cerrar(this)
                true
            } else {
                false
            }
        }

        savedInstanceState?.getString(KEY_REF)?.let { ref = db.collection("solicitudes").document(it) }
        binding.btnEnviar.setOnClickListener { enviar() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        ref?.let { outState.putString(KEY_REF, it.id) }
    }

    private fun validar(til: TextInputLayout, texto: String, max: Int): Boolean {
        til.error = when {
            texto.isBlank() -> getString(R.string.login_empty_error)
            !Solicitud.textoValido(texto, max) -> getString(R.string.error_max_caracteres, max)
            else -> null
        }
        return til.error == null
    }

    private fun enviar() {
        val sector = binding.etSector.text?.toString().orEmpty()
        val comentario = binding.etComentario.text?.toString().orEmpty()
        val sectorOk = validar(binding.tilSector, sector, Solicitud.MAX_SECTOR)
        val comentarioOk = validar(binding.tilComentario, comentario, Solicitud.MAX_COMENTARIO)
        if (!sectorOk || !comentarioOk) return

        val user = FirebaseAuth.getInstance().currentUser ?: return Sesion.cerrar(this)
        val prioridad = when (binding.rgPrioridad.checkedRadioButtonId) {
            R.id.rbBaja -> "Baja"
            R.id.rbAlta -> "Alta"
            else -> "Media"
        }
        val doc = ref ?: db.collection("solicitudes").document().also { ref = it }

        setEnviando(true)
        lifecycleScope.launch {
            try {
                val nombre = try {
                    db.collection("usuarios").document(user.uid).get().await().getString("nombre")
                } catch (e: Exception) {
                    null
                }
                doc.set(
                    hashMapOf(
                        "uid" to user.uid,
                        "nombre" to (nombre ?: user.email.orEmpty()),
                        "sector" to sector.trim(),
                        "comentario" to comentario.trim(),
                        "prioridad" to prioridad,
                        "fecha" to FieldValue.serverTimestamp()
                    )
                ).await()
                ref = null
                binding.etSector.text = null
                binding.etComentario.text = null
                binding.rgPrioridad.check(R.id.rbMedia)
                binding.tvEstado.text = getString(R.string.estado_enviado)
            } catch (e: Exception) {
                // Se mantiene ref: el reintento escribe el mismo doc
                binding.tvEstado.text = getString(R.string.estado_error)
            }
            setEnviando(false)
        }
    }

    private fun setEnviando(enviando: Boolean) {
        binding.btnEnviar.isEnabled = !enviando
        if (enviando) binding.tvEstado.text = getString(R.string.estado_enviando)
    }

    companion object {
        private const val KEY_REF = "solicitud_ref"
    }
}
