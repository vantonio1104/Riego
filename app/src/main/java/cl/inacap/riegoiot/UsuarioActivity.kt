package cl.inacap.riegoiot

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cl.inacap.riegoiot.databinding.ActivityUsuarioBinding

class UsuarioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUsuarioBinding

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
        // Fase 4: formulario "Solicitud de riego"
    }
}
