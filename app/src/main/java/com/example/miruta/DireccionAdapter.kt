package com.example.miruta

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DireccionAdapter(private val direcciones: MutableList<Direccion>) :
    RecyclerView.Adapter<DireccionAdapter.DireccionViewHolder>() {

    class DireccionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDireccion: TextView = itemView.findViewById(R.id.tvDireccion)
        val btnEliminar: Button = itemView.findViewById(R.id.btnEliminar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DireccionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_direccion, parent, false)
        return DireccionViewHolder(view)
    }

    override fun onBindViewHolder(holder: DireccionViewHolder, position: Int) {
        val direccion = direcciones[position]
        holder.tvDireccion.text = direccion.texto

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
