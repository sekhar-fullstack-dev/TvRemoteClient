package com.example.tvremoteclient.viewmodel;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class MainActivityViewModel extends ViewModel {
    public MutableLiveData<Boolean> isConencted = new MutableLiveData<>(false);
}
