package com.catwatch.detector.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.catwatch.detector.R
import com.catwatch.detector.data.CatEventEntity
import com.catwatch.detector.databinding.DialogFullscreenImageBinding
import com.catwatch.detector.databinding.ItemSessionCarouselPageBinding
import com.catwatch.detector.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CatEventDetailDialogFragment : DialogFragment() {

    private var _binding: DialogFullscreenImageBinding? = null
    private val binding get() = _binding!!

    private var sessionEvents = mutableListOf<CatEventEntity>()
    private var initialEventId: Long = -1L
    private var onDeleteListener: ((CatEventEntity, () -> Unit) -> Unit)? = null

    companion object {
        const val TAG = "CatEventDetailDialogFragment"

        fun show(
            fragmentManager: FragmentManager,
            session: List<CatEventEntity>,
            initialEvent: CatEventEntity,
            onDelete: (CatEventEntity, () -> Unit) -> Unit
        ) {
            val fragment = CatEventDetailDialogFragment()
            fragment.sessionEvents = session.toMutableList()
            fragment.initialEventId = initialEvent.id
            fragment.onDeleteListener = onDelete
            fragment.show(fragmentManager, TAG)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFullscreenImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (sessionEvents.isEmpty()) {
            dismiss()
            return
        }

        val adapter = SessionCarouselAdapter(sessionEvents)
        binding.sessionViewPager.adapter = adapter
        binding.sessionViewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL

        // Posiciona no evento clicado dentro da sessão
        val initialIndex = sessionEvents.indexOfFirst { it.id == initialEventId }.coerceAtLeast(0)
        binding.sessionViewPager.setCurrentItem(initialIndex, false)

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        fun updateMetadata(position: Int) {
            if (position in sessionEvents.indices) {
                val current = sessionEvents[position]
                val total = sessionEvents.size

                val stepText = if (current.isConfirmedDrinking || current.eventType == "DRINKING") {
                    "Confirmação de Hidratação"
                } else {
                    "Aproximação Inicial"
                }

                binding.dialogSessionCounter.text = "Foto ${position + 1} de $total • $stepText"
                binding.dialogTimestamp.text = "Data/Hora: ${dateFormat.format(Date(current.timestamp))}"
                binding.dialogConfidence.text = "Confiança: %.1f%%".format(current.confidence * 100)
                binding.dialogFilePath.text = "Arquivo: ${current.filePath}"
            }
        }

        updateMetadata(initialIndex)

        binding.sessionViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateMetadata(position)
            }
        })

        binding.dialogCloseButton.setOnClickListener {
            dismiss()
        }

        binding.dialogDeleteButton.setOnClickListener {
            val currentPosition = binding.sessionViewPager.currentItem
            if (currentPosition in sessionEvents.indices) {
                val currentEvent = sessionEvents[currentPosition]
                AlertDialog.Builder(requireContext())
                    .setTitle("Excluir Registro")
                    .setMessage("Deseja excluir permanentemente este registro da sessão?")
                    .setPositiveButton("Excluir") { _, _ ->
                        onDeleteListener?.invoke(currentEvent) {
                            Toast.makeText(requireContext(), "Registro excluído.", Toast.LENGTH_SHORT).show()
                            sessionEvents.removeAt(currentPosition)
                            if (sessionEvents.isEmpty()) {
                                dismiss()
                            } else {
                                adapter.notifyItemRemoved(currentPosition)
                                val nextPos = currentPosition.coerceAtMost(sessionEvents.size - 1)
                                binding.sessionViewPager.setCurrentItem(nextPos, false)
                                updateMetadata(nextPos)
                            }
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class SessionCarouselAdapter(
        private val items: List<CatEventEntity>
    ) : RecyclerView.Adapter<SessionCarouselAdapter.CarouselViewHolder>() {

        inner class CarouselViewHolder(val pageBinding: ItemSessionCarouselPageBinding) :
            RecyclerView.ViewHolder(pageBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarouselViewHolder {
            val pageBinding = ItemSessionCarouselPageBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return CarouselViewHolder(pageBinding)
        }

        override fun onBindViewHolder(holder: CarouselViewHolder, position: Int) {
            val item = items[position]
            val binding = holder.pageBinding

            if (item.isConfirmedDrinking || item.eventType == "DRINKING") {
                binding.carouselBadge.text = "Hidratação Confirmada"
                binding.carouselBadge.setBackgroundResource(R.drawable.bg_badge_drinking)
                binding.carouselBadge.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_water_drop, 0, 0, 0)
            } else {
                binding.carouselBadge.text = "Aproximação"
                binding.carouselBadge.setBackgroundResource(R.drawable.bg_badge_approach)
                binding.carouselBadge.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_visibility, 0, 0, 0)
            }

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val bitmap = ImageUtils.loadHighResBitmap(item.filePath, 1080, 1080)
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        binding.carouselImageView.setImageBitmap(bitmap)
                    } else {
                        binding.carouselImageView.setImageResource(android.R.drawable.ic_menu_camera)
                    }
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
