package com.catwatch.detector.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.catwatch.detector.R
import com.catwatch.detector.data.CatEventEntity
import com.catwatch.detector.databinding.DialogFullscreenImageBinding
import com.catwatch.detector.utils.ImageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CatEventDetailDialog {

    fun show(
        context: Context,
        event: CatEventEntity,
        coroutineScope: CoroutineScope,
        session: List<CatEventEntity> = listOf(event),
        onDelete: (CatEventEntity, () -> Unit) -> Unit
    ) {
        if (context is androidx.fragment.app.FragmentActivity) {
            CatEventDetailDialogFragment.show(
                context.supportFragmentManager,
                session,
                event,
                onDelete
            )
            return
        }

        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val dialogBinding = DialogFullscreenImageBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(dialogBinding.root)

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        dialogBinding.dialogTimestamp.text = "Data/Hora: ${dateFormat.format(Date(event.timestamp))}"
        dialogBinding.dialogConfidence.text = "Confiança: %.1f%%".format(event.confidence * 100)
        dialogBinding.dialogFilePath.text = "Arquivo: ${event.filePath}"

        if (event.isConfirmedDrinking || event.eventType == "DRINKING") {
            dialogBinding.dialogSessionCounter.text = "Foto 1 de 1 • Hidratação Confirmada"
        } else {
            dialogBinding.dialogSessionCounter.text = "Foto 1 de 1 • Aproximação Inicial"
        }

        dialogBinding.dialogCloseButton.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.dialogDeleteButton.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Excluir Registro")
                .setMessage("Deseja excluir permanentemente este registro?")
                .setPositiveButton("Excluir") { _, _ ->
                    onDelete(event) {
                        Toast.makeText(context, "Registro excluído.", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        dialog.show()
    }
}