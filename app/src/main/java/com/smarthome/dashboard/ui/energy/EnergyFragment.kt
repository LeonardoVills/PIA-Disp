package com.smarthome.dashboard.ui.energy

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.smarthome.dashboard.R
import com.smarthome.dashboard.data.repository.SmartHomeRepository
import com.smarthome.dashboard.databinding.FragmentEnergyBinding

class EnergyFragment : Fragment() {

    private var _binding: FragmentEnergyBinding? = null
    private val binding get() = _binding!!
    private var currentPeriod = PeriodType.HOURLY

    enum class PeriodType { HOURLY, DAILY, MONTHLY }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEnergyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPeriodTabs()
        loadHourlyData()
        setupDeviceUsageList()
    }

    private fun setupPeriodTabs() {
        binding.btnHourly.setOnClickListener {
            currentPeriod = PeriodType.HOURLY
            updateTabSelection()
            loadHourlyData()
        }
        binding.btnDaily.setOnClickListener {
            currentPeriod = PeriodType.DAILY
            updateTabSelection()
            loadDailyData()
        }
        binding.btnMonthly.setOnClickListener {
            currentPeriod = PeriodType.MONTHLY
            updateTabSelection()
            loadMonthlyData()
        }
    }

    private fun updateTabSelection() {
        val activeColor = ContextCompat.getColor(requireContext(), R.color.primary)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.surface_variant)
        val activeTextColor = Color.WHITE
        val inactiveTextColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        listOf(
            Pair(binding.btnHourly, currentPeriod == PeriodType.HOURLY),
            Pair(binding.btnDaily, currentPeriod == PeriodType.DAILY),
            Pair(binding.btnMonthly, currentPeriod == PeriodType.MONTHLY)
        ).forEach { (btn, active) ->
            btn.setBackgroundColor(if (active) activeColor else inactiveColor)
            btn.setTextColor(if (active) activeTextColor else inactiveTextColor)
        }
    }

    private fun loadHourlyData() {
        val data = SmartHomeRepository.getHourlyConsumption()
        val totalKwh = data.sumOf { it.totalKwh }
        val totalCost = data.sumOf { it.totalCostMXN }
        val peakHour = data.maxByOrNull { it.totalKwh }

        binding.tvPeriodTitle.text = "Consumo por Hora - Hoy"
        binding.tvTotalKwh.text = "%.2f kWh".format(totalKwh)
        binding.tvTotalCost.text = "$%.2f MXN".format(totalCost)
        binding.tvPeakInfo.text = "Pico: ${peakHour?.hour}:00 h (%.2f kWh)".format(peakHour?.totalKwh ?: 0.0)

        val entries = data.mapIndexed { index, h -> BarEntry(index.toFloat(), h.totalKwh.toFloat()) }
        val labels = data.map { "%02d".format(it.hour) }
        setupBarChart(entries, labels, "kWh/hora")
    }

    private fun loadDailyData() {
        val data = SmartHomeRepository.getDailyConsumption()
        val totalKwh = data.sumOf { it.totalKwh }
        val totalCost = data.sumOf { it.totalCostMXN }
        val peakDay = data.maxByOrNull { it.totalKwh }

        binding.tvPeriodTitle.text = "Consumo Diario - Esta Semana"
        binding.tvTotalKwh.text = "%.1f kWh".format(totalKwh)
        binding.tvTotalCost.text = "$%.2f MXN".format(totalCost)
        binding.tvPeakInfo.text = "Pico: ${peakDay?.dayLabel} (%.1f kWh)".format(peakDay?.totalKwh ?: 0.0)

        val entries = data.mapIndexed { index, d -> BarEntry(index.toFloat(), d.totalKwh.toFloat()) }
        val labels = data.map { it.dayLabel }
        setupBarChart(entries, labels, "kWh/día")
    }

    private fun loadMonthlyData() {
        val data = SmartHomeRepository.getMonthlyConsumption()
        val totalKwh = data.sumOf { it.totalKwh }
        val totalCost = data.sumOf { it.totalCostMXN }
        val peakMonth = data.maxByOrNull { it.totalKwh }

        binding.tvPeriodTitle.text = "Consumo Mensual - Últimos 6 meses"
        binding.tvTotalKwh.text = "%.0f kWh".format(totalKwh)
        binding.tvTotalCost.text = "$%.0f MXN".format(totalCost)
        binding.tvPeakInfo.text = "Pico: ${peakMonth?.monthLabel} (%.0f kWh)".format(peakMonth?.totalKwh ?: 0.0)

        val entries = data.mapIndexed { index, m -> BarEntry(index.toFloat(), m.totalKwh.toFloat()) }
        val labels = data.map { it.monthLabel }
        setupBarChart(entries, labels, "kWh/mes")
    }

    private fun setupBarChart(entries: List<BarEntry>, labels: List<String>, yLabel: String) {
        val chart = binding.barChart
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
        val accentColor = ContextCompat.getColor(requireContext(), R.color.accent)

        val maxEntry = entries.maxByOrNull { it.y }
        val colors = entries.map { entry ->
            if (entry.y == maxEntry?.y) accentColor else primaryColor
        }

        val dataSet = BarDataSet(entries, yLabel).apply {
            this.colors = colors
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            valueTextSize = 9f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "%.1f".format(value)
            }
        }

        chart.apply {
            data = BarData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            setDrawGridBackground(false)
            setBackgroundColor(Color.TRANSPARENT)

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                textSize = 10f
            }
            axisLeft.apply {
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(requireContext(), R.color.divider)
                axisMinimum = 0f
            }
            axisRight.isEnabled = false
            animateY(800)
            invalidate()
        }
    }

    private fun setupDeviceUsageList() {
        val stats = SmartHomeRepository.getDeviceUsageStats()
        val adapter = DeviceUsageAdapter(stats.take(8))
        binding.rvDeviceUsage.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
