package piuk.blockchain.android.ui.customviews.account

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.coincore.AssetAction
import com.blockchain.coincore.BlockchainAccount
import com.blockchain.coincore.CryptoAccount
import com.blockchain.coincore.FiatAccount
import com.blockchain.coincore.fiat.FiatCustodialAccount
import com.blockchain.coincore.fiat.LinkedBankAccount
import com.blockchain.coincore.impl.AllWalletsAccount
import com.blockchain.commonarch.presentation.base.ActivityIndicator
import com.blockchain.commonarch.presentation.base.trackProgress
import com.blockchain.domain.paymentmethods.model.FundsLocks
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.kotlin.zipWith
import java.io.Serializable
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.ItemAccountSelectBankBinding
import piuk.blockchain.android.databinding.ItemAccountSelectCryptoBinding
import piuk.blockchain.android.databinding.ItemAccountSelectFiatBinding
import piuk.blockchain.android.databinding.ItemAccountSelectGroupBinding
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.ui.adapters.AdapterDelegatesManager
import piuk.blockchain.android.ui.adapters.DelegationAdapter
import piuk.blockchain.android.ui.customviews.BlockchainListDividerDecor
import piuk.blockchain.android.ui.customviews.IntroHeaderView
import piuk.blockchain.android.util.context

typealias StatusDecorator = (BlockchainAccount) -> CellDecorator

interface AccountsListItem

data class AccountLocks(
    val fundsLocks: FundsLocks? = null
) : Serializable, AccountsListItem

internal data class SelectableAccountItem(
    val item: AccountListViewItem,
    var isSelected: Boolean,
) : AccountsListItem

class AccountList @JvmOverloads constructor(
    ctx: Context,
    val attr: AttributeSet? = null,
    defStyle: Int = 0
) : RecyclerView(ctx, attr, defStyle) {

    private val disposables = CompositeDisposable()
    private val uiScheduler = AndroidSchedulers.mainThread()
    private var lastSelectedAccount: BlockchainAccount? = null
    var activityIndicator: ActivityIndicator? = null

    init {
        setBackgroundColor(Color.WHITE)
        setFadingEdgeLength(resources.getDimensionPixelSize(R.dimen.size_small))
        isVerticalFadingEdgeEnabled = true
        layoutManager = LinearLayoutManager(
            context,
            VERTICAL,
            false
        )
        addItemDecoration(
            BlockchainListDividerDecor(context)
        )
        itemAnimator = null
    }

    fun initialise(
        source: Single<List<AccountListViewItem>>,
        status: StatusDecorator = { DefaultCellDecorator() },
        accountsLocks: Single<List<AccountLocks>> = Single.just(emptyList()),
        introView: IntroHeaderView? = null,
        shouldShowSelectionStatus: Boolean = false,
        assetAction: AssetAction? = null,
    ) {
        removeAllHeaderDecorations()

        introView?.let {
            addItemDecoration(
                HeaderDecoration.with(context)
                    .parallax(0.5f)
                    .setView(it)
                    .build()
            )
        }

        if (adapter == null) {
            adapter = AccountsDelegateAdapter(
                statusDecorator = status,
                onAccountClicked = { onAccountSelected(it) },
                onLockItemSelected = { onLockItemSelected(it) },
                showSelectionStatus = shouldShowSelectionStatus,
                assetAction = assetAction,
            )
        }
        loadItems(accountsSource = source, accountsLocksSource = accountsLocks)
    }

    fun loadItems(
        accountsSource: Single<List<AccountListViewItem>>,
        accountsLocksSource: Single<List<AccountLocks>>,
        showLoader: Boolean = true,
    ) {
        val loader = if (showLoader) activityIndicator else null
        disposables += accountsSource.zipWith(accountsLocksSource.onErrorReturn { emptyList() })
            .observeOn(uiScheduler)
            .doOnSubscribe {
                onListLoading()
            }
            .trackProgress(loader)
            .subscribeBy(
                onSuccess = { (accounts, extraInfos) ->
                    (adapter as? AccountsDelegateAdapter)?.items = extraInfos + accounts.map { account ->
                        SelectableAccountItem(account, false)
                    }
                    onListLoaded(accounts.isEmpty())

                    lastSelectedAccount?.let { account ->
                        updatedSelectedAccount(account)
                        lastSelectedAccount = null
                    }
                },
                onError = {
                    onLoadError(it)
                }
            )
    }

    fun updatedSelectedAccount(selectedAccount: BlockchainAccount) {
        with(adapter as AccountsDelegateAdapter) {
            if (items.isNotEmpty()) {
                val previouslySelectedPosition =
                    items.filterIsInstance<SelectableAccountItem>().indexOfFirst { account ->
                        account.isSelected
                    }
                if (previouslySelectedPosition != -1) {
                    (items[previouslySelectedPosition] as? SelectableAccountItem)?.isSelected = false
                    notifyItemChanged(previouslySelectedPosition)
                }

                val positionToSelect = items.filterIsInstance<SelectableAccountItem>().indexOfFirst { account ->
                    account.item == selectedAccount
                }
                if (positionToSelect != -1) {
                    (items[positionToSelect] as? SelectableAccountItem)?.isSelected = true
                    notifyItemChanged(positionToSelect)
                }
            } else {
                // if list is empty, we're in a race condition between loading and selecting, so store value and check
                // it once items loaded
                lastSelectedAccount = selectedAccount
            }
        }
    }

    fun clearSelectedAccount() {
        (adapter as AccountsDelegateAdapter).items =
            (adapter as AccountsDelegateAdapter).items.map { item ->
                (item as? SelectableAccountItem)?.let { selectableAccountItem ->
                    SelectableAccountItem(
                        selectableAccountItem.item, false
                    )
                } ?: item
            }
    }

    var onLoadError: (Throwable) -> Unit = {}
    var onAccountSelected: (BlockchainAccount) -> Unit = {}
    var onLockItemSelected: (AccountLocks) -> Unit = {}
    var onListLoaded: (isEmpty: Boolean) -> Unit = {}
    var onListLoading: () -> Unit = {}

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        disposables.clear()
    }
}

private class AccountsDelegateAdapter(
    statusDecorator: StatusDecorator,
    onAccountClicked: (BlockchainAccount) -> Unit,
    onLockItemSelected: (AccountLocks) -> Unit,
    showSelectionStatus: Boolean,
    assetAction: AssetAction? = null,
) : DelegationAdapter<AccountsListItem>(AdapterDelegatesManager(), emptyList()) {

    override var items: List<AccountsListItem> = emptyList()
        set(value) {
            val diffResult =
                DiffUtil.calculateDiff(AccountsDiffUtil(this.items, value))
            field = value
            diffResult.dispatchUpdatesTo(this)
        }

    init {
        with(delegatesManager) {
            addAdapterDelegate(
                CryptoAccountDelegate(
                    statusDecorator,
                    onAccountClicked,
                    showSelectionStatus,
                )
            )
            addAdapterDelegate(
                FiatAccountDelegate(
                    statusDecorator,
                    onAccountClicked,
                    showSelectionStatus
                )
            )
            addAdapterDelegate(
                AllWalletsAccountDelegate(
                    statusDecorator,
                    onAccountClicked,
                    compositeDisposable
                )
            )
            addAdapterDelegate(
                BankAccountDelegate(
                    onAccountClicked,
                    showSelectionStatus,
                    assetAction
                )
            )

            addAdapterDelegate(
                AccountLocksDelegate(
                    onLockItemSelected
                )
            )
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        (holder as? CryptoSingleAccountViewHolder)?.dispose()
    }
}

private class CryptoAccountDelegate(
    private val statusDecorator: StatusDecorator,
    private val onAccountClicked: (CryptoAccount) -> Unit,
    private val showSelectionStatus: Boolean,
) : AdapterDelegate<AccountsListItem> {

    override fun isForViewType(items: List<AccountsListItem>, position: Int): Boolean =
        (items[position] as? SelectableAccountItem)?.item is AccountListViewItem.Crypto

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        CryptoSingleAccountViewHolder(
            showSelectionStatus,
            ItemAccountSelectCryptoBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun onBindViewHolder(
        items: List<AccountsListItem>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as CryptoSingleAccountViewHolder).bind(
        items[position] as SelectableAccountItem,
        statusDecorator,
        onAccountClicked
    )
}

private class CryptoSingleAccountViewHolder(
    private val showSelectionStatus: Boolean,
    private val binding: ItemAccountSelectCryptoBinding
) : RecyclerView.ViewHolder(binding.root), DisposableViewHolder {

    fun bind(
        selectableAccountItem: SelectableAccountItem,
        statusDecorator: StatusDecorator,
        onAccountClicked: (CryptoAccount) -> Unit,
    ) {
        with(binding) {
            if (showSelectionStatus) {
                if (selectableAccountItem.isSelected) {
                    cryptoAccountParent.background = ContextCompat.getDrawable(context, R.drawable.item_selected_bkgd)
                } else {
                    cryptoAccountParent.background = null
                }
            }
            cryptoAccount.updateItem(
                item = selectableAccountItem.item as AccountListViewItem.Crypto,
                onAccountClicked = onAccountClicked,
                cellDecorator = statusDecorator(selectableAccountItem.item.account),
            )
        }
    }

    override fun dispose() {
        binding.cryptoAccount.dispose()
    }
}

private class FiatAccountDelegate(
    private val statusDecorator: StatusDecorator,
    private val onAccountClicked: (FiatAccount) -> Unit,
    private val showSelectionStatus: Boolean
) : AdapterDelegate<AccountsListItem> {
    override fun isForViewType(items: List<AccountsListItem>, position: Int): Boolean =
        (items[position] as? SelectableAccountItem)?.item?.account is FiatCustodialAccount

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        FiatAccountViewHolder(
            showSelectionStatus,
            ItemAccountSelectFiatBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun onBindViewHolder(items: List<AccountsListItem>, position: Int, holder: RecyclerView.ViewHolder) =
        (holder as FiatAccountViewHolder).bind(
            items[position] as SelectableAccountItem,
            statusDecorator,
            onAccountClicked
        )
}

private class FiatAccountViewHolder(
    private val showSelectionStatus: Boolean,
    private val binding: ItemAccountSelectFiatBinding
) : RecyclerView.ViewHolder(binding.root), DisposableViewHolder {

    fun bind(
        selectableAccountItem: SelectableAccountItem,
        statusDecorator: StatusDecorator,
        onAccountClicked: (FiatAccount) -> Unit
    ) {
        with(binding) {
            if (showSelectionStatus) {
                if (selectableAccountItem.isSelected) {
                    fiatContainer.background = ContextCompat.getDrawable(context, R.drawable.item_selected_bkgd)
                } else {
                    fiatContainer.background = null
                }
            }
            fiatContainer.alpha = 1f
            fiatAccount.updateAccount(
                selectableAccountItem.item.account as FiatAccount,
                statusDecorator(selectableAccountItem.item.account),
                onAccountClicked
            )
        }
    }

    override fun dispose() {
        binding.fiatAccount.dispose()
    }
}

private class BankAccountDelegate(
    private val onAccountClicked: (LinkedBankAccount) -> Unit,
    private val showSelectionStatus: Boolean,
    private val assetAction: AssetAction?
) : AdapterDelegate<AccountsListItem> {

    override fun isForViewType(items: List<AccountsListItem>, position: Int): Boolean =
        (items[position] as? SelectableAccountItem)?.item?.account is LinkedBankAccount

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        BankAccountViewHolder(
            showSelectionStatus,
            ItemAccountSelectBankBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun onBindViewHolder(
        items: List<AccountsListItem>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as BankAccountViewHolder).bind(
        items[position] as SelectableAccountItem,
        onAccountClicked,
        assetAction
    )
}

private class BankAccountViewHolder(
    private val showSelectionStatus: Boolean,
    private val binding: ItemAccountSelectBankBinding
) : RecyclerView.ViewHolder(binding.root), DisposableViewHolder {

    fun bind(
        selectableAccountItem: SelectableAccountItem,
        onAccountClicked: (LinkedBankAccount) -> Unit,
        assetAction: AssetAction?
    ) {
        with(binding) {
            if (showSelectionStatus) {
                if (selectableAccountItem.isSelected) {
                    bankContainer.background = ContextCompat.getDrawable(context, R.drawable.item_selected_bkgd)
                } else {
                    bankContainer.background = null
                }
            }
            bankContainer.alpha = 1f
            bankAccount.updateAccount(
                account = selectableAccountItem.item.account as LinkedBankAccount,
                action = assetAction,
                onAccountClicked = onAccountClicked
            )
        }
    }

    override fun dispose() {
        // nothing to dispose
    }
}

private class AllWalletsAccountDelegate(
    private val statusDecorator: StatusDecorator,
    private val onAccountClicked: (BlockchainAccount) -> Unit,
    private val compositeDisposable: CompositeDisposable
) : AdapterDelegate<AccountsListItem> {

    override fun isForViewType(items: List<AccountsListItem>, position: Int): Boolean =
        (items[position] as? SelectableAccountItem)?.item?.account is AllWalletsAccount

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        AllWalletsAccountViewHolder(
            compositeDisposable,
            ItemAccountSelectGroupBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun onBindViewHolder(
        items: List<AccountsListItem>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as AllWalletsAccountViewHolder).bind(
        items[position] as SelectableAccountItem,
        statusDecorator,
        onAccountClicked
    )
}

private class AllWalletsAccountViewHolder(
    private val compositeDisposable: CompositeDisposable,
    private val binding: ItemAccountSelectGroupBinding
) : RecyclerView.ViewHolder(binding.root), DisposableViewHolder {

    fun bind(
        selectableAccountItem: SelectableAccountItem,
        statusDecorator: StatusDecorator,
        onAccountClicked: (BlockchainAccount) -> Unit
    ) {
        with(binding) {

            accountGroup.updateAccount(selectableAccountItem.item.account as AllWalletsAccount)
            accountGroup.alpha = 1f

            compositeDisposable += statusDecorator(selectableAccountItem.item.account).isEnabled()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { root.setOnClickListener { } }
                .subscribeBy(
                    onSuccess = { isEnabled ->
                        if (isEnabled) {
                            root.setOnClickListener { onAccountClicked(selectableAccountItem.item.account) }
                            accountGroup.alpha = 1f
                        } else {
                            accountGroup.alpha = .6f
                            root.setOnClickListener { }
                        }
                    }
                )
        }
    }

    override fun dispose() {
        binding.accountGroup.dispose()
    }
}

interface DisposableViewHolder {
    fun dispose()
}
